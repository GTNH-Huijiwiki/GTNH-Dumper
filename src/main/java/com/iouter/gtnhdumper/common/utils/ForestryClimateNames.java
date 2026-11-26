package com.iouter.gtnhdumper.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import forestry.api.core.EnumHumidity;
import forestry.api.core.EnumTemperature;
import forestry.api.genetics.AlleleManager;

/** The names Forestry shows for its climates, by id; kept apart so that Forestry stays optional. */
final class ForestryClimateNames {

    private ForestryClimateNames() {}

    static Map<String, Map<String, String>> all() {
        Map<String, String> temperatures = new LinkedHashMap<>();
        for (EnumTemperature value : EnumTemperature.values()) {
            temperatures.put(value.name(), AlleleManager.climateHelper.toDisplay(value));
        }
        Map<String, String> humidities = new LinkedHashMap<>();
        for (EnumHumidity value : EnumHumidity.values()) {
            humidities.put(value.name(), AlleleManager.climateHelper.toDisplay(value));
        }
        Map<String, Map<String, String>> names = new LinkedHashMap<>();
        names.put("temperature", temperatures);
        names.put("humidity", humidities);
        return names;
    }
}
