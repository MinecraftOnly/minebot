package net.famzangl.minecraft.minebot.ai.scanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import net.famzangl.minecraft.minebot.Pos;
import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.ItemFilter;
import net.famzangl.minecraft.minebot.ai.scanner.ChestBlockHandler.ChestData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class ChestBlockHandler extends RangeBlockHandler<ChestData> {

	private static final int[] IDS = new int[] { Block
			.getIdFromBlock(Blocks.chest) };

	public static class ChestData {
		private final Pos pos;
		private final ArrayList<ItemFilter> allowedItems = new ArrayList<ItemFilter>();
		private final ArrayList<ItemFilter> fullItems = new ArrayList<ItemFilter>();
		private final ArrayList<ItemFilter> emptyItems = new ArrayList<ItemFilter>();
		public Pos secondaryPos;

		public ChestData(Pos pos) {
			super();
			this.pos = pos;
		}

		public boolean isItemAllowed(ItemStack stack) {
			for (ItemFilter f : allowedItems) {
				if (f.matches(stack)) {
					return true;
				}
			}
			return false;
		}

		public boolean couldPutItem(ItemStack stack) {
			for (ItemFilter f : fullItems) {
				if (f.matches(stack)) {
					return false;
				}
			}
			return isItemAllowed(stack);
		}

		public boolean couldTakeItem(ItemStack stack) {
			for (ItemFilter f : emptyItems) {
				if (f.matches(stack)) {
					return false;
				}
			}
			return isItemAllowed(stack);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((pos == null) ? 0 : pos.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ChestData other = (ChestData) obj;
			if (pos == null) {
				if (other.pos != null)
					return false;
			} else if (!pos.equals(other.pos))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ChestData [pos=" + pos + ", allowedItems=" + allowedItems
					+ "]";
		}

		public void allowItem(final ItemStack displayed) {
			allowedItems.add(new SameItemFilter(displayed));
		}

		public Pos getPos() {
			return pos;
		}

		public Pos getSecondaryPos() {
			return secondaryPos;
		}

		public void markAsFullFor(ItemStack s, boolean full) {
			removeFrom(fullItems, s);
			if (full) {
				fullItems.add(new SameItemFilter(s));
			}
		}

		public void markAsEmptyFor(ItemStack s, boolean empty) {
			removeFrom(emptyItems, s);
			if (empty) {
				emptyItems.add(new SameItemFilter(s));
			}
		}

		private void removeFrom(ArrayList<ItemFilter> list, ItemStack s) {
			Iterator<ItemFilter> iterator = list.iterator();
			while (iterator.hasNext()) {
				ItemFilter filter = iterator.next();
				if (filter.matches(s)) {
					iterator.remove();
				}
			}
		}
	}

	private final Hashtable<Pos, ChestData> chests = new Hashtable<Pos, ChestData>();

	@Override
	public int[] getIds() {
		return IDS;
	}

	@Override
	protected void addPositionToCache(AIHelper helper, Pos pos, ChestData c) {
		super.addPositionToCache(helper, pos, c);
		if (c.secondaryPos != null) {
			super.addPositionToCache(helper, c.secondaryPos, c);
		}
	}

	@Override
	protected Collection<Entry<Pos, ChestData>> getTargetPositions() {
		return chests.entrySet();
	}
	
	@Override
	public void scanBlock(AIHelper helper, int id, int x, int y, int z) {
		if (helper.getBlock(x, y, z) instanceof BlockChest) {
			AxisAlignedBB abb = AxisAlignedBB.getBoundingBox(x - 1, y, z - 1,
					x + 2, y + 1, z + 2);
			List<EntityItemFrame> frames = helper.getMinecraft().theWorld
					.getEntitiesWithinAABB(EntityItemFrame.class, abb);
			for (EntityItemFrame f : frames) {
				ForgeDirection direction = getDirection(f);
				if (direction == null) {
					continue;
				}
				int frameX = f.field_146063_b;
				int frameY = f.field_146064_c;
				int frameZ = f.field_146062_d;
				if (x == frameX && y == frameY && z == frameZ) {
					// Yeah, frame attached.
					registerChest(new Pos(x, y, z), f);
				}
			}
		}

	}

	private void registerChest(Pos pos, EntityItemFrame f) {
		ChestData chest = null;
		for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.UP,
				ForgeDirection.NORTH, ForgeDirection.SOUTH,
				ForgeDirection.EAST, ForgeDirection.WEST }) {
			Pos p = pos.add(d.offsetX, 0, d.offsetZ);
			if (chests.containsKey(p)) {
				chest = chests.get(p);
				if (!chest.pos.equals(pos)) {
					chest.secondaryPos = pos;
				}
			}
		}
		if (chest == null) {
			chest = new ChestData(pos);
			chests.put(pos, chest);
		}
		ItemStack displayed = f.getDisplayedItem();
		if (displayed != null) {
			chest.allowItem(displayed);
		}
	}

	/**
	 * Gets the direction of whatever this frame is attached to
	 * 
	 * @param f
	 * @return
	 */
	private ForgeDirection getDirection(EntityItemFrame f) {
		switch (f.hangingDirection) {
		case 0:
			return ForgeDirection.NORTH;
		case 2:
			return ForgeDirection.SOUTH;
		case 1:
			return ForgeDirection.EAST;
		case 3:
			return ForgeDirection.WEST;
		}
		return null;
	}
}