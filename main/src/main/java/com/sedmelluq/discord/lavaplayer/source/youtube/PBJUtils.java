package com.sedmelluq.discord.lavaplayer.source.youtube;

import com.sedmelluq.discord.lavaplayer.tools.Units;

public class PBJUtils {
    protected static long parseDuration(String duration) {
        String[] parts = duration.split(":");

        if (parts.length == 3) { // hh::mm:ss
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            return (hours * 3600000) + (minutes * 60000) + (seconds * 1000);
        } else if (parts.length == 2) { // mm:ss
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            return (minutes * 60000) + (seconds * 1000);
        } else {
            return Units.DURATION_MS_UNKNOWN;
        }
    }
}
