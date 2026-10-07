package com.example.examplemod.scale;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Optional-dependency boundary. This class deliberately has no Pehkui types in its signatures. */
public final class PehkuiScaleSupport {
    private static final String PEHKUI_MOD_ID = "pehkui";
    private static final String INTEGRATION_CLASS = "com.example.examplemod.scale.PehkuiScaleIntegration";
    private static final ConcurrentMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private static volatile boolean available;
    private static volatile Class<?> integrationClass;

    private PehkuiScaleSupport() {
    }

    public static boolean isLoaded() {
        return available;
    }

    public static void initializeIfAvailable() {
        available = ModList.get().isLoaded(PEHKUI_MOD_ID);
        if (available) {
            invoke("initialize", new Class<?>[0]);
        }
    }

    public static boolean ensurePlayerScale(Player player) {
        return isLoaded() && (boolean) invoke("ensurePlayerScale", new Class<?>[]{Player.class}, player);
    }

    public static boolean ensureHuntScale(LivingEntity entity) {
        return isLoaded() && (boolean) invoke("ensureHuntScale", new Class<?>[]{LivingEntity.class}, entity);
    }

    public static boolean ensureWandScale(LivingEntity entity) {
        return isLoaded() && (boolean) invoke("ensureWandScale", new Class<?>[]{LivingEntity.class}, entity);
    }

    public static void removeWandScale(LivingEntity entity) {
        if (isLoaded()) {
            invoke("removeWandScale", new Class<?>[]{LivingEntity.class}, entity);
        }
    }

    public static void clearPlayerScale(Player player) {
        if (isLoaded()) {
            invoke("clearPlayerScale", new Class<?>[]{Player.class}, player);
        }
    }

    public static void restoreSizeDerivedScaling(LivingEntity entity) {
        if (isLoaded()) {
            invoke("restoreSizeDerivedScaling", new Class<?>[]{LivingEntity.class}, entity);
        }
    }

    public static float getEffectiveScale(Entity entity) {
        if (isLoaded()) {
            return (float) invoke("getEffectiveScale", new Class<?>[]{Entity.class}, entity);
        }
        return entity instanceof LivingEntity living ? living.getScale() : 1.0F;
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = METHODS.computeIfAbsent(methodName, ignored -> {
                try {
                    return getIntegrationClass().getMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException exception) {
                    throw new IllegalStateException("Pehkui integration API mismatch: " + methodName, exception);
                }
            });
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Pehkui integration failed: " + methodName, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to invoke Pehkui integration: " + methodName, exception);
        }
    }

    private static Class<?> getIntegrationClass() {
        Class<?> result = integrationClass;
        if (result == null) {
            try {
                result = Class.forName(INTEGRATION_CLASS, true, PehkuiScaleSupport.class.getClassLoader());
                integrationClass = result;
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Pehkui integration class is missing", exception);
            }
        }
        return result;
    }
}
