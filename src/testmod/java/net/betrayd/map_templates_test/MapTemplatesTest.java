package net.betrayd.map_templates_test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class MapTemplatesTest implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("map-templates-test");

    @Override
    public void onInitialize() {
        LOGGER.info("Hello World!");
    }
    
}