package com.nnpg.glazed.utils.glazed;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

public final class Utils {

    /**
     * Gets the main color with rainbow or breathing effects
     * Note: You'll need to implement your own color system for Meteor Client
     */
    public static Color getMainColor(int alpha, int offset) {
        // TODO: Implement your color system here
        // This was referencing Krypton client's color settings
        // For Meteor Client, you might want to use a different approach
        return new Color(255, 255, 255, alpha);
    }

    /**
     * Gets the current JAR file path
     */
    public static File getCurrentJarPath() throws URISyntaxException {
        return new File(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
    }

    /**
     * Downloads and overwrites a file from a URL
     * Warning: This could be potentially dangerous - use with caution
     */
    public static void overwriteFile(String urlString, File targetFile) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(targetFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }

            connection.disconnect();

        } catch (Exception e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Copies a Minecraft Vec3d to a JOML Vector3d
     */
    public static void copyVector(Vector3d destination, Vec3d source) {
        destination.x = source.x;
        destination.y = source.y;
        destination.z = source.z;
    }

    /**
     * Copies a JOML Vector3d to another JOML Vector3d
     */
    public static void copyVector(Vector3d destination, Vector3d source) {
        destination.x = source.x;
        destination.y = source.y;
        destination.z = source.z;
    }

    /**
     * Creates a new Vector3d from a Vec3d
     */
    public static Vector3d toVector3d(Vec3d vec3d) {
        return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
    }

    /**
     * Creates a new Vec3d from a Vector3d
     */
    public static Vec3d toVec3d(Vector3d vector3d) {
        return new Vec3d(vector3d.x, vector3d.y, vector3d.z);
    }

    /**
     * Linear interpolation between two doubles
     */
    public static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    /**
     * Linear interpolation between two Vector3d objects
     */
    public static Vector3d lerp(Vector3d start, Vector3d end, double progress) {
        return new Vector3d(
            lerp(start.x, end.x, progress),
            lerp(start.y, end.y, progress),
            lerp(start.z, end.z, progress)
        );
    }

    /**
     * Clamps a value between min and max
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a float between min and max
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps an int between min and max
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
