package openblocks.common.tileentity;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidHandler;
import openblocks.Config;
import openblocks.OpenBlocks;
import openblocks.client.gui.GuiSprinkler;
import openblocks.common.container.ContainerSprinkler;
import openmods.api.IHasGui;
import openmods.api.INeighbourAwareTile;
import openmods.api.ISurfaceAttachment;
import openmods.fakeplayer.FakePlayerPool;
import openmods.fakeplayer.FakePlayerPool.PlayerUser;
import openmods.fakeplayer.OpenModsFakePlayer;
import openmods.include.IncludeInterface;
import openmods.include.IncludeOverride;
import openmods.inventory.GenericInventory;
import openmods.inventory.IInventoryProvider;
import openmods.inventory.TileEntityInventory;
import openmods.inventory.legacy.ItemDistribution;
import openmods.liquids.GenericFluidHandler;
import openmods.sync.SyncableFlags;
import openmods.sync.SyncableTank;
import openmods.tileentity.SyncedTileEntity;

public class TileEntitySprinkler extends SyncedTileEntity implements ISurfaceAttachment, IInventoryProvider, IHasGui, ITickable, INeighbourAwareTile {

	private static final ItemStack BONEMEAL = new ItemStack(Items.DYE, 1, 15);

	private static final Random RANDOM = new Random();

	private static final double[] SPRINKER_DELTA = new double[] { 0.2, 0.25, 0.5 };
	private static final int[] SPRINKER_MOD = new int[] { 1, 5, 20 };

	private boolean hasBonemeal = false;

	private boolean needsTankUpdate;

	public enum Flags {
		enabled
	}

	private SyncableFlags flags;
	private SyncableTank tank;

	public int ticks;

	private final GenericInventory inventory = registerInventoryCallback(new TileEntityInventory(this, "sprinkler", true, 9) {
		@Override
		public boolean isItemValidForSlot(int i, ItemStack itemstack) {
			return itemstack != null && itemstack.isItemEqual(BONEMEAL);
		}
	});

	@IncludeInterface
	private final IFluidHandler tankWrapper = new GenericFluidHandler.Drain(tank);

	@Override
	protected void createSyncedFields() {
		flags = SyncableFlags.create(Flags.values().length);
		tank = new SyncableTank(Config.sprinklerInternalTank, FluidRegistry.WATER, OpenBlocks.Fluids.xpJuice);
	}

	private static int selectFromRange(int range) {
		return RANDOM.nextInt(2 * range + 1) - range;
	}

	private void attemptFertilize() {
		if (!(worldObj instanceof WorldServer)) return;
		final int fertilizerChance = hasBonemeal? Config.sprinklerBonemealFertizizeChance : Config.sprinklerFertilizeChance;
		if (RANDOM.nextDouble() < 1.0 / fertilizerChance) {
			FakePlayerPool.instance.executeOnPlayer((WorldServer)worldObj, new PlayerUser() {
				@Override
				public void usePlayer(OpenModsFakePlayer fakePlayer) {
					final int x = selectFromRange(Config.sprinklerEffectiveRange);
					final int z = selectFromRange(Config.sprinklerEffectiveRange);

					for (int y = -1; y <= 1; y++) {
						BlockPos target = pos.add(x, y, z);

						if (ItemDye.applyBonemeal(BONEMEAL.copy(), worldObj, target, fakePlayer))
							break;

					}
				}
			});
		}
	}

	@Override
	public Object getServerGui(EntityPlayer player) {
		return new ContainerSprinkler(player.inventory, this);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		return new GuiSprinkler(new ContainerSprinkler(player.inventory, this));
	}

	@Override
	public boolean canOpenGui(EntityPlayer player) {
		return true;
	}

	private static final double SPRAY_SIDE_SCATTER = Math.toRadians(25);

	private void sprayParticles() {
		if (tank.getFluidAmount() > 0) {
			// 0 = All, 1 = Decreased, 2 = Minimal
			final int particleSetting = OpenBlocks.proxy.getParticleSettings();
			if (particleSetting > 2) return;

			final int fillFactor = SPRINKER_MOD[particleSetting];

			if ((ticks % fillFactor) != 0) return;
			final EnumFacing blockYawRotation = getOrientation().north();
			final double nozzleAngle = getSprayDirection();
			final double sprayForwardVelocity = Math.sin(Math.toRadians(nozzleAngle * 25));

			final int offsetZ = blockYawRotation.getFrontOffsetZ();
			final int offsetX = blockYawRotation.getFrontOffsetX();
			// TODO 1.8.9 verify
			final double forwardVelocityX = sprayForwardVelocity * offsetZ / -2;
			final double forwardVelocityZ = sprayForwardVelocity * offsetX / 2;

			final double sprinklerDelta = SPRINKER_DELTA[particleSetting];
			double outletPosition = -0.5;

			while (outletPosition <= 0.5) {
				final double spraySideVelocity = Math.sin(SPRAY_SIDE_SCATTER * (RANDOM.nextDouble() - 0.5));

				final double sideVelocityX = spraySideVelocity * offsetX;
				final double sideVelocityZ = spraySideVelocity * offsetZ;

				Vec3d vec = new Vec3d(
						forwardVelocityX + sideVelocityX,
						0.35,
						forwardVelocityZ + sideVelocityZ);

				OpenBlocks.proxy.spawnLiquidSpray(worldObj, tank.getFluid(),
						pos.getX() + 0.5 + (outletPosition * 0.6 * offsetX),
						pos.getY() + 0.2,
						pos.getZ() + 0.5 + (outletPosition * 0.6 * offsetZ),
						0.3f, 0.7f, vec);

				outletPosition += sprinklerDelta;
			}
		}
	}

	@Override
	public void update() {
		if (!worldObj.isRemote) {

			if (tank.getFluidAmount() <= 0) {
				if (needsTankUpdate) {
					tank.updateNeighbours(worldObj, pos);
					needsTankUpdate = false;
				}

				tank.fillFromSide(worldObj, pos, EnumFacing.DOWN);
			}

			if (ticks % Config.sprinklerBonemealConsumeRate == 0) {
				hasBonemeal = ItemDistribution.consumeFirstInventoryItem(inventory, BONEMEAL);
			}

			if (ticks % Config.sprinklerWaterConsumeRate == 0) {
				setEnabled(tank.drain(1, true) != null);
				sync();
			}
		}

		ticks++;

		// simplified this action because only one of these will execute
		// depending on worldObj.isRemote
		if (isEnabled()) {
			if (worldObj.isRemote) sprayParticles();
			else attemptFertilize();
		}
	}

	private void setEnabled(boolean b) {
		flags.set(Flags.enabled, b);
	}

	private boolean isEnabled() {
		return flags.get(Flags.enabled);
	}

	@Override
	public EnumFacing getSurfaceDirection() {
		return EnumFacing.DOWN;
	}

	/**
	 * Get spray direction of Sprinkler particles
	 *
	 * @return float from -1f to 1f indicating the direction, left to right of the particles
	 */
	public float getSprayDirection() {
		if (isEnabled()) { return MathHelper.sin(ticks * 0.02f); }
		return 0;
	}

	@IncludeOverride
	public boolean canDrain(EnumFacing from, Fluid fluid) {
		return false;
	}

	@Override
	@IncludeInterface
	public IInventory getInventory() {
		return inventory;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		inventory.writeToNBT(tag);

		return tag;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inventory.readFromNBT(tag);
	}

	@Override
	public void validate() {
		super.validate();
		this.needsTankUpdate = true;
	}

	@Override
	public void onNeighbourChanged(Block block) {
		this.needsTankUpdate = true;
	}
}
