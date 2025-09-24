package link.botwmcs.qubit.registrations;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public interface RegistryHelper<T> {
    <I extends T> Supplier<I> register(ResourceLocation id, Supplier<? extends I> supplier);
}

