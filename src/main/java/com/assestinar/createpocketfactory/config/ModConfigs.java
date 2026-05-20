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

    public static boolean showEntrancePreview() {
        return CLIENT.showEntrancePreview.get();
    }

    public static boolean rotateEntrancePreview() {
        return CLIENT.rotateEntrancePreview.get();
    }

    public static boolean showLinkedBlockParticles() {
        return CLIENT.showLinkedBlockParticles.get();
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
        private final ModConfigSpec.BooleanValue showEntrancePreview;
        private final ModConfigSpec.BooleanValue rotateEntrancePreview;
        private final ModConfigSpec.BooleanValue showLinkedBlockParticles;

        private Client(ModConfigSpec.Builder builder) {
            builder.translation("create_pocket_factory.configuration.rendering").push("rendering");
            showEntrancePreview = builder
                .translation("create_pocket_factory.configuration.showEntrancePreview")
                    .comment(
                            "If true, the entrance renders a miniature snapshot of the pocket factory.",
                            "If false, the entrance renders a rotating Pocket Factory Core model instead.")
                    .define("showEntrancePreview", true);
            rotateEntrancePreview = builder
                .translation("create_pocket_factory.configuration.rotateEntrancePreview")
                    .comment(
                            "If true, the entrance display rotates.",
                            "If false, both the miniature preview and the core fallback stay still.")
                    .define("rotateEntrancePreview", false);
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