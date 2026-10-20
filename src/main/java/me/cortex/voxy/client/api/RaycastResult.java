package me.cortex.voxy.client.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public record RaycastResult(BlockPos blockPos, Direction face, Vec3 hitLocation, BlockState blockState) {}
