package openblocks.client.renderer.tileentity.tank;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import openmods.utils.Diagonal;

public class RenderConnection {
	private final DoubledCoords coords;

	public RenderConnection(DoubledCoords coords) {
		this.coords = coords;
	}

	public boolean isPositionEqualTo(int x, int y, int z, EnumFacing dir) {
		return coords.isSameAs(x, y, z, dir);
	}

	public boolean isPositionEqualTo(BlockPos pos, EnumFacing dir) {
		return coords.isSameAs(pos, dir);
	}

	public boolean isPositionEqualTo(int x, int y, int z, Diagonal dir) {
		return coords.isSameAs(x, y, z, dir);
	}

	public boolean isPositionEqualTo(BlockPos pos, Diagonal dir) {
		return coords.isSameAs(pos, dir);
	}
}