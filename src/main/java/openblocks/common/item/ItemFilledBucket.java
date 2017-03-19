package openblocks.common.item;

import net.minecraft.init.Items;
import openmods.item.ItemGeneric;

public class ItemFilledBucket extends ItemGeneric {

	public ItemFilledBucket() {
		setContainerItem(Items.BUCKET);
		setMaxStackSize(1);
	}
}
