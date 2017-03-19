package openblocks.common.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import openblocks.common.item.ItemPaintBrush;
import openblocks.common.item.ItemPaintCan;
import openmods.api.IActivateAwareTile;
import openmods.sync.SyncableInt;
import openmods.sync.drops.DroppableTileEntity;
import openmods.sync.drops.StoreOnDrop;
import openmods.utils.BlockUtils;

public class TileEntityPaintCan extends DroppableTileEntity implements IActivateAwareTile {

	@StoreOnDrop(name = ItemPaintCan.TAG_COLOR)
	private SyncableInt color;

	@StoreOnDrop(name = ItemPaintCan.TAG_AMOUNT)
	private SyncableInt amount;

	@Override
	protected void createSyncedFields() {
		color = new SyncableInt();
		amount = new SyncableInt();
	}

	@Override
	public boolean onBlockActivated(EntityPlayer player, EnumFacing side, float hitX, float hitY, float hitZ) {
		if (amount.get() > 0) { // TODO 1.10 verify sounds effects of removing 'isRemote'
			ItemStack heldStack = player.getHeldItemMainhand();
			if (heldStack != null && heldStack.getItem() instanceof ItemPaintBrush) {
				ItemPaintBrush.setColor(heldStack, color.get());
				heldStack.setItemDamage(0);
				amount.modify(-1);
				sync();
				worldObj.playSound(null, player.getPosition(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 0.1F, 1.2F);
			}
		}

		if (amount.get() <= 0 && !worldObj.isRemote) {
			ItemStack item = new ItemStack(Items.BUCKET);
			BlockUtils.dropItemStackInWorld(worldObj, pos, item);
			worldObj.setBlockToAir(pos);
		}
		return false;
	}

	public int getColor() {
		return color.get();
	}

	public int getAmount() {
		return amount.get();
	}

	public void setAmount(int amt) {
		amount.set(amt);
	}

}
