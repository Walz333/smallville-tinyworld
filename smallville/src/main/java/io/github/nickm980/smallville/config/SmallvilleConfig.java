package io.github.nickm980.smallville.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nickm980.smallville.config.prompts.Prompts;

public class SmallvilleConfig {
    private static Prompts prompts;
    private static GeneralConfig config;
    private static final Logger LOG = LoggerFactory.getLogger(SmallvilleConfig.class);

    private SmallvilleConfig() {

    }

    public static Prompts getPrompts() {
	if (prompts == null) {
	    LOG.info("Loading prompts...");
	    prompts = loadYamlFile("prompts.yaml", Prompts.class);
	}

	return prompts;
    }

    public static GeneralConfig getConfig() {
	if (config == null) {
	    LOG.info("Loading config.yaml...");
	    config = loadYamlFile("config.yaml", GeneralConfig.class);
	}

	return config;
    }

    private static InputStream loadInputStream(String path) {
	Path[] candidates = new Path[] { Paths.get(path), Paths.get("src", "main", "resources", path),
		Paths.get("smallville", "src", "main", "resources", path) };

	InputStream inputStream = null;

	for (Path file : candidates) {
	    if (!Files.exists(file)) {
		continue;
	    }

	    LOG.debug("Configuration file found at {}", file.toAbsolutePath());
	    try {
		inputStream = Files.newInputStream(file);
		break;
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	if (inputStream == null) {
	    LOG.debug("Loading default configuration");
	    inputStream = SmallvilleConfig.class.getResourceAsStream("/" + path);
	}

	if (inputStream == null) {
	    LOG.error("No {} found. It must be either in resources folder or next to jar", path);
	}

	return inputStream;
    }

    private static JsonNode loadJsonFile(String file) {
	InputStream stream = loadInputStream(file);
	ObjectMapper mapper = new ObjectMapper();
	JsonNode result = null;
	
	try {
	    result = mapper.readTree(stream);
	    stream.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}

	return result;
    }

    public static <T> T loadYamlFile(String file, Class<T> clazz) {
	Yaml yaml = new Yaml();
	InputStream stream = loadInputStream(file);
	T result = yaml.loadAs(stream, clazz);

	try {
	    stream.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}

	return result;
    }
}
