package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.CreatePocketFactory;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.PipeConnection;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class LinkedPumpSourceConnection extends PipeConnection {
    private static final Field SOURCE_FIELD = resolveSourceField();
    private static final ThreadLocal<Set<String>> ACTIVE_SOURCE_LOOKUPS = ThreadLocal.withInitial(HashSet::new);

    private final LinkedPumpBlockEntity owner;

    public LinkedPumpSourceConnection(LinkedPumpBlockEntity owner, Direction side) {
        super(side);
        this.owner = owner;
    }

    @Override
    public boolean determineSource(Level world, BlockPos pos) {
        Direction pullSide = owner.getPullSide();
        if (pullSide != null && side == pullSide && owner.hasBinding()) {
            LinkedPumpBlockEntity.RemoteSourceSpec spec = owner.resolveRemoteSourceSpec();
            if (spec != null) {
                setSource(Optional.of(new LinkedRemotePipeSource(owner, side)));
                return true;
            }
            setSource(Optional.empty());
            return false;
        }
        return super.determineSource(world, pos);
    }

    private void setSource(Optional<FlowSource> source) {
        try {
            SOURCE_FIELD.set(this, source);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update PipeConnection source", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<FlowSource> getSource(PipeConnection connection) {
        try {
            return (Optional<FlowSource>) SOURCE_FIELD.get(connection);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read PipeConnection source", exception);
        }
    }

    private static Field resolveSourceField() {
        try {
            Field field = PipeConnection.class.getDeclaredField("source");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access PipeConnection source field", exception);
        }
    }

    private static final class LinkedRemotePipeSource extends FlowSource {
        private final LinkedPumpBlockEntity owner;
        private @javax.annotation.Nullable FlowSource cachedBackingSource;
        private @javax.annotation.Nullable String cachedBackingKey;
        private int debugLogCooldown;

        private LinkedRemotePipeSource(LinkedPumpBlockEntity owner, Direction localSide) {
            super(new BlockFace(owner.getBlockPos(), localSide));
            this.owner = owner;
        }

        private Optional<FlowSource> resolveRemoteBackingSource(LinkedPumpBlockEntity.RemoteSourceSpec spec, boolean initialize) {
            String backingKey = spec.level().dimension().location() + "@" + spec.pos() + "#" + spec.pullSide();
            if (cachedBackingSource != null && backingKey.equals(cachedBackingKey)) {
                if (initialize) {
                    BlockEntity remotePumpEntity = spec.level().getBlockEntity(spec.pos());
                    if (remotePumpEntity == null || remotePumpEntity.isRemoved()) {
                        cachedBackingSource = null;
                        cachedBackingKey = null;
                        emitDebugLog("backing_source_remote_missing", spec, null, FluidStack.EMPTY, false);
                        return Optional.empty();
                    }
                    cachedBackingSource.manageSource(spec.level(), remotePumpEntity);
                }
                emitDebugLog("backing_source_cached", spec, cachedBackingSource, FluidStack.EMPTY, true);
                return Optional.of(cachedBackingSource);
            }

            BlockEntity remotePumpEntity = spec.level().getBlockEntity(spec.pos());
            if (remotePumpEntity == null || remotePumpEntity.isRemoved()) {
                cachedBackingSource = null;
                cachedBackingKey = null;
                emitDebugLog("backing_source_remote_missing", spec, null, FluidStack.EMPTY, false);
                return Optional.empty();
            }

            PipeConnection remotePullConnection = new PipeConnection(spec.pullSide());
            if (!remotePullConnection.determineSource(spec.level(), spec.pos())) {
                cachedBackingSource = null;
                cachedBackingKey = null;
                emitDebugLog("backing_source_determine_failed", spec, null, FluidStack.EMPTY, false);
                return Optional.empty();
            }

            remotePullConnection.manageSource(spec.level(), spec.pos(), remotePumpEntity);
            Optional<FlowSource> remoteSource = getSource(remotePullConnection);
            cachedBackingSource = remoteSource.orElse(null);
            cachedBackingKey = remoteSource.isPresent() ? backingKey : null;
            emitDebugLog("backing_source_resolved", spec, cachedBackingSource, FluidStack.EMPTY, remoteSource.isPresent());
            return remoteSource;
        }

        private void emitDebugLog(String stage, LinkedPumpBlockEntity.RemoteSourceSpec spec,
                                  @javax.annotation.Nullable FlowSource source, FluidStack provided, boolean resolved) {
            Level level = owner.getLevel();
            if (level == null || level.isClientSide || --debugLogCooldown > 0) {
                return;
            }
            debugLogCooldown = 40;

            String sourceType = source == null ? "null" : source.getClass().getSimpleName();
            boolean endpoint = source != null && source.isEndpoint();
            boolean hasHandler = source != null && source.provideHandler() != null && source.provideHandler().getCapability() != null;

            CreatePocketFactory.LOGGER.info(
                "LinkedPump source stage={} owner={} remotePos={} remotePull={} resolved={} sourceType={} endpoint={} hasHandler={} provided={}",
                stage,
                owner.getBlockPos(),
                spec.pos(),
                spec.pullSide(),
                resolved,
                sourceType,
                endpoint,
                hasHandler,
                provided
            );
        }

        @Override
        public void manageSource(Level world, BlockEntity networkBE) {
            LinkedPumpBlockEntity.RemoteSourceSpec spec = owner.resolveRemoteSourceSpec();
            if (spec == null) {
                cachedBackingSource = null;
                cachedBackingKey = null;
                return;
            }
            resolveRemoteBackingSource(spec, true);
        }

        @Override
        public FluidStack provideFluid(Predicate<FluidStack> extractionPredicate) {
            LinkedPumpBlockEntity.RemoteSourceSpec spec = owner.resolveRemoteSourceSpec();
            if (spec == null) {
                return FluidStack.EMPTY;
            }

            String lookupKey = owner.getBlockPos() + "->" + spec.level().dimension().location() + "@" + spec.pos();
            Set<String> activeLookups = ACTIVE_SOURCE_LOOKUPS.get();
            if (!activeLookups.add(lookupKey)) {
                return FluidStack.EMPTY;
            }

            try {
                Optional<FlowSource> remoteSource = resolveRemoteBackingSource(spec, true);
                if (remoteSource.isEmpty()) {
                    emitDebugLog("provide_no_source", spec, null, FluidStack.EMPTY, false);
                    return FluidStack.EMPTY;
                }

                FluidStack provided = remoteSource.get().provideFluid(extractionPredicate);
                emitDebugLog("provide_result", spec, remoteSource.get(), provided, true);
                return extractionPredicate.test(provided) ? provided : FluidStack.EMPTY;
            } finally {
                activeLookups.remove(lookupKey);
                if (activeLookups.isEmpty()) {
                    ACTIVE_SOURCE_LOOKUPS.remove();
                }
            }
        }

        @Override
        public boolean isEndpoint() {
            LinkedPumpBlockEntity.RemoteSourceSpec spec = owner.resolveRemoteSourceSpec();
            if (spec == null) {
                return false;
            }
            return resolveRemoteBackingSource(spec, true).map(FlowSource::isEndpoint).orElse(false);
        }

        @Override
        public ICapabilityProvider<net.neoforged.neoforge.fluids.capability.IFluidHandler> provideHandler() {
            LinkedPumpBlockEntity.RemoteSourceSpec spec = owner.resolveRemoteSourceSpec();
            if (spec == null) {
                return null;
            }
            return resolveRemoteBackingSource(spec, true).map(FlowSource::provideHandler).orElse(null);
        }
    }
}