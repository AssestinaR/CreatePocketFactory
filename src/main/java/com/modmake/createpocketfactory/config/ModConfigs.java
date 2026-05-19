package com.assestinar.createpocketfactory.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfigs {
    public static final ModConfigSpec SERVER_SPEC;
    private static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private ModConfigs() {
    }

    public static boolean requirePocketBoundaryForLinkedBindings() {
        return SERVER.requirePocketBoundaryForLinkedBindings.get();
    }

    private static final class Server {
        private final ModConfigSpec.BooleanValue requirePocketBoundaryForLinkedBindings;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("linking");
            requirePocketBoundaryForLinkedBindings = builder
                    .comment(
                            "If true, linked bindings require one endpoint inside a pocket factory and the other outside it.",
                            "If false, same-side binding is allowed as long as at least one endpoint is inside a pocket factory.")
                    .define("requirePocketBoundaryForLinkedBindings", false);
            builder.pop();
        }
    }
}