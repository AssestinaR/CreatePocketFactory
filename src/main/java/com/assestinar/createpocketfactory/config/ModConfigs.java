package com.assestinar.createpocketfactory.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfigs {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    private static final Server SERVER;
    private static final Client CLIENT;

    static {
        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    private ModConfigs() {
    }

    public static boolean requirePocketBoundaryForLinkedBindings() {
        return SERVER.requirePocketBoundaryForLinkedBindings.get();
    }

    public static EntranceProjectionMode entranceProjectionMode() {
        return CLIENT.entranceProjectionMode.get();
    }

    public static boolean rotateEntrancePreview() {
        return CLIENT.rotateEntrancePreview.get();
    }

    public static boolean renderProjectionBlockEntities() {
        return CLIENT.renderProjectionBlockEntities.get();
    }

    public static boolean renderDynamicProjectionParts() {
        return CLIENT.renderDynamicProjectionParts.get();
    }

    public static boolean showLinkedBlockParticles() {
        return CLIENT.showLinkedBlockParticles.get();
    }

    public enum EntranceProjectionMode {
        CORE_ONLY,
        MINIATURE,
        LARGE_WITH_CORE;

        public boolean showsMiniaturePreview() {
            return this == MINIATURE;
        }

        public boolean showsLargeProjection() {
            return this == LARGE_WITH_CORE;
        }
    }

    private static final class Server {
        private final ModConfigSpec.BooleanValue requirePocketBoundaryForLinkedBindings;

        private Server(ModConfigSpec.Builder builder) {
            builder.translation("create_pocket_factory.configuration.linking").push("linking");
            requirePocketBoundaryForLinkedBindings = builder
                .translation("create_pocket_factory.configuration.requirePocketBoundaryForLinkedBindings")
                    .comment(
                            "If true, linked bindings require one endpoint inside a pocket factory and the other outside it.",
                            "If false, same-side binding is allowed as long as at least one endpoint is inside a pocket factory.")
                    .define("requirePocketBoundaryForLinkedBindings", false);
            builder.pop();
        }
    }

    private static final class Client {
        private final ModConfigSpec.EnumValue<EntranceProjectionMode> entranceProjectionMode;
        private final ModConfigSpec.BooleanValue rotateEntrancePreview;
        private final ModConfigSpec.BooleanValue renderProjectionBlockEntities;
        private final ModConfigSpec.BooleanValue renderDynamicProjectionParts;
        private final ModConfigSpec.BooleanValue showLinkedBlockParticles;

        private Client(ModConfigSpec.Builder builder) {
            builder.translation("create_pocket_factory.configuration.rendering").push("rendering");
            entranceProjectionMode = builder
            .translation("create_pocket_factory.configuration.entranceProjectionMode")
                    .comment(
                    "Controls how the entrance and held bound entrance render the pocket factory preview.",
                    "CORE_ONLY renders only the rotating core.",
                    "MINIATURE renders the miniature preview on the entrance.",
                    "LARGE_WITH_CORE keeps the entrance on the core and enables the held large projection.")
                .defineEnum("entranceProjectionMode", EntranceProjectionMode.MINIATURE);
            rotateEntrancePreview = builder
                .translation("create_pocket_factory.configuration.rotateEntrancePreview")
                    .comment(
                            "If true, the entrance display rotates.",
                    "If false, both the miniature preview and the core fallback stay still.")
                    .define("rotateEntrancePreview", false);
            renderProjectionBlockEntities = builder
                .translation("create_pocket_factory.configuration.renderProjectionBlockEntities")
                    .comment(
                        "If true, both the miniature and the large projection render Create block entity overlay parts.",
                        "If false, both projection sizes skip the BER path entirely and only keep cached static block models.")
                    .define("renderProjectionBlockEntities", true);
            renderDynamicProjectionParts = builder
                .translation("create_pocket_factory.configuration.renderDynamicProjectionParts")
                    .comment(
                        "If true, the large projection also renders Create block entity animation parts such as shafts, chain wheels, or steam engine linkages.",
                        "If false, only the static block model is rendered for the projection.")
                    .define("renderDynamicProjectionParts", true);
            builder.pop();

            builder.translation("create_pocket_factory.configuration.linkedBlockEffects").push("linkedBlockEffects");
            showLinkedBlockParticles = builder
                .translation("create_pocket_factory.configuration.showLinkedBlockParticles")
                    .comment(
                            "If true, nearby linked blocks occasionally emit a small upward particle as a visual marker.",
                            "The effect is client-side only and only appears for nearby linked blocks in view.")
                    .define("showLinkedBlockParticles", true);
            builder.pop();
        }
    }
}