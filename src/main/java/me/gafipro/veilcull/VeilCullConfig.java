package me.gafipro.veilcull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VeilCullConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Logger LOGGER = LoggerFactory.getLogger(VeilCullConfig.class);

    /**
     * The first generated configuration is deliberately disabled.
     * The value is changed and persisted by /interceptmsg.
     */
    public boolean interceptEnabled = false;
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

            if (loaded == null) {
                throw new IOException("Configuration parsed to null");
            }

            interceptEnabled = loaded.interceptEnabled;
            interceptObserver = loaded.interceptObserver == null
                    ? ""
                    : loaded.interceptObserver;
        } catch (IOException | RuntimeException e) {
            interceptEnabled = false;
            interceptObserver = "";
            LOGGER.warn("Could not load {}, using safe defaults.", path, e);
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
            LOGGER.warn("Could not save {}.", path, e);
        }
    }
}
