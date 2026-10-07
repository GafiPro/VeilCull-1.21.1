package me.gafipro.veillull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VeilCullConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public boolean interceptEnabled = false;
    public String interceptPlayer = "mvcraft_";
    public String interceptObserver = "";

    private Path getPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("veilcull.json");
    }

    public void load() {
        Path path = getPath();

        try {
            if (!Files.exists(path)) {
                save();
                return;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            VeilCullConfig loaded = GSON.fromJson(json, VeilCullConfig.class);
            if (loaded != null) {
                interceptEnabled = loaded.interceptEnabled;
                interceptPlayer = loaded.interceptPlayer;
                interceptObserver = loaded.interceptObserver;
            }
        } catch (IOException | RuntimeException e) {
            // Keep safe defaults when the configuration is unreadable.
            interceptEnabled = false;
            interceptPlayer = "mvcraft_";
            interceptObserver = "";
        }
    }

    public void save() {
        Path path = getPath();

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    GSON.toJson(this),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            // Do not crash the server because a prank config could not be written.
        }
    }
}
