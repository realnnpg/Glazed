package com.nnpg.glazed.utils.glazed;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

public final class Utils {

    public static File getCurrentJarPath() throws URISyntaxException {
        return new File(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
    }

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

    public static void copyVector(Vector3d destination, Vec3d source) {
        destination.x = source.x;
        destination.y = source.y;
        destination.z = source.z;
    }

    public static void copyVector(Vector3d destination, Vector3d source) {
        destination.x = source.x;
        destination.y = source.y;
        destination.z = source.z;
    }

    public static Vector3d toVector3d(Vec3d vec3d) {
        return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
    }

    public static Vec3d toVec3d(Vector3d vector3d) {
        return new Vec3d(vector3d.x, vector3d.y, vector3d.z);
    }

    public static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    public static Vector3d lerp(Vector3d start, Vector3d end, double progress) {
        return new Vector3d(
            lerp(start.x, end.x, progress),
            lerp(start.y, end.y, progress),
            lerp(start.z, end.z, progress)
        );
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
