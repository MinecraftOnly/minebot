/*******************************************************************************
 * This file is part of Minebot.
 *
 * Minebot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minebot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Minebot.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package net.famzangl.minecraft.minebot.ai.path;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSets;
import net.famzangl.minecraft.minebot.ai.path.world.WorldData;
import net.famzangl.minecraft.minebot.ai.task.DestroyInRangeTask;
import net.famzangl.minecraft.minebot.ai.task.RunOnceTask;
import net.famzangl.minecraft.minebot.ai.task.TaskOperations;
import net.famzangl.minecraft.minebot.ai.task.WaitTask;
import net.famzangl.minecraft.minebot.ai.task.error.StringTaskError;
import net.famzangl.minecraft.minebot.ai.task.move.HorizontalMoveTask;
import net.famzangl.minecraft.minebot.ai.task.move.JumpMoveTask;
import net.famzangl.minecraft.minebot.ai.task.place.PlantSaplingTask;
import net.famzangl.minecraft.minebot.ai.utils.BlockCounter;
import net.famzangl.minecraft.minebot.ai.utils.BlockCuboid;
import net.famzangl.minecraft.minebot.build.block.WoodType;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

/**
 * This searches for trees (vertical rows of logs), walks to the bottom most and
 * then destroys them.
 * <p>
 * There is special case handling for the big, 2x2 trees. If one of those is
 * found, a staircase all the way up is build. After reaching the top, that
 * staircase is walked downwards destroying all logs above the player on every
 * layer.
 * 
 * @author Michael Zangl
 *
 */
public class TreePathFinder extends MovePathFinder {
	private static final BlockSet TREE_STUFF = BlockSets.LEAVES
			.unionWith(BlockSets.LOGS);

	private class LargeTreeState {
		private int minX;
		private int minZ;

		/**
		 * The height at which we started. We need to dig down to here when
		 * digging down again.
		 */
		private int minY;

		/**
		 * How high we should dig. This is the first block that is no trunk any
		 * more.
		 */
		private int topY;

		/**
		 * An offset (0..3) to compute the stairs.
		 */
		private int stairOffset;

		public LargeTreeState(int minX, int minY, int minZ) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
		}

		public LargeTreeState(BlockPos p) {
			this(p.getX(), p.getY(), p.getZ());
		}

		/**
		 * The position we need to stand on a stair.
		 * 
		 * @param y
		 * @return
		 */
		private BlockPos getPosition(int y) {
			return new BlockPos(getRelativeX(y) + minX, y, getRelativeZ(y)
					+ minZ);
		}

		private int getRelativeX(int y) {
			return ((y + stairOffset) >> 1) & 1;
		}

		private int getRelativeZ(int y) {
			return ((y + stairOffset + 1) >> 1) & 1;
		}

		public void scanTreeHeight(WorldData world, BlockPos ignoredPlayerPos) {
			for (topY = minY; topY < 255; topY++) {
				BlockPos corner = new BlockPos(minX, topY, minZ);
				BlockCuboid trunk = new BlockCuboid(corner, corner.add(1, 0, 1));
				int trunkBlocks = BlockCounter.countBlocks(world, trunk, logs)[0];
				int should = topY <= ignoredPlayerPos.getY() + 1 ? 3 : 4;
				System.out.println("For y=" + topY + ": " + trunkBlocks);
				if (trunkBlocks < should) {
					break;
				}
			}
		}

		public int getTreeHeight() {
			return topY - minY;
		}

		/**
		 * Sets the height offset so that currentPos is a stair.
		 * 
		 * @param currentPos
		 *            The position.
		 */
		public void setYOffsetByPosition(BlockPos currentPos) {
			for (stairOffset = 0; stairOffset < 4; stairOffset++) {
				if (getPosition(currentPos.getY()).equals(currentPos)) {
					return;
				}
			}
			throw new IllegalArgumentException(currentPos
					+ " is not on the trunk.");
		}

		public void addTasks(AIHelper h) {
			BlockPos pos = h.getPlayerPosition();
			if (!isValidPlayerPosition(pos)) {
				// TODO: Print a warning?
				System.err.println("Illegal start position " + pos + " for "
						+ this);
				return;
			}

			// dig up until the top.
			BlockPos lastPos = pos;
			for (int y = pos.getY() + 1; y < topY; y++) {
				BlockPos digTo = getPosition(y);
				if (!BlockSets.safeSideAndCeilingAround(world,
						digTo.add(0, 1, 0))
						|| !BlockSets.safeSideAround(world, digTo)
						|| !BlockSets.SAFE_GROUND.isAt(world,
								digTo.add(0, -1, 0))) {
					break;
				}
				addTask(new JumpMoveTask(digTo, lastPos.getX(), lastPos.getZ()));
				lastPos = digTo;
			}
			// now destroy all
			for (int y = lastPos.getY(); y >= pos.getY(); y--) {
				BlockPos digTo = getPosition(y);
				addTask(new HorizontalMoveTask(digTo));
				// TODO: Destroy all above y.
				addTask(new DestroyInRangeTask(new BlockPos(minX, y, minZ),
						new BlockPos(minX + 1, y + 4, minZ + 1)));
			}
		}

		private boolean isValidPlayerPosition(BlockPos pos) {
			return !(pos.getY() < minY || pos.getY() >= topY || !getPosition(
					pos.getY()).equals(pos));
		}

		@Override
		public String toString() {
			return "LargeTreeState [minX=" + minX + ", minZ=" + minZ
					+ ", minY=" + minY + ", topY=" + topY + ", stairOffset="
					+ stairOffset + "]";
		}

	}

	public class SwitchToLargeTreeTask extends RunOnceTask {
		private LargeTreeState state;

		public SwitchToLargeTreeTask(LargeTreeState state) {
			this.state = state;
		}

		@Override
		protected void runOnce(AIHelper h, TaskOperations o) {
			if (state != null
					&& !state.isValidPlayerPosition(h.getPlayerPosition())) {
				o.desync(new StringTaskError("Not in a tree."));
				largeTree = null;
			} else {
				largeTree = state;
			}
		}
	}

	private final WoodType type;
	private final BlockSet logs;
	private final boolean replant;
	/**
	 * If this is set, we are cutting a large tree. We don't use the pathfinding
	 * in this case.
	 */
	private LargeTreeState largeTree;

	public TreePathFinder(WoodType type, boolean replant) {
		this.type = type;
		this.replant = replant;
		this.logs = type == null ? BlockSets.LOGS : type.getLogBlocks();
		shortFootBlocks = shortFootBlocks.unionWith(BlockSets.LEAVES);
		shortHeadBlocks = shortHeadBlocks.unionWith(BlockSets.LEAVES);
	}

	private static final int TREE_HEIGHT = 7;

	/**
	 * This is a special override that allows us to addd tasks for the large
	 * tree without requireing to pathfind. FIXME: Find a nicer, uniform
	 * solution.
	 * 
	 * @return
	 */
	public boolean addTasksForLargeTree(AIHelper h) {
		if (largeTree != null) {
			largeTree.addTasks(h);
			addTask(new SwitchToLargeTreeTask(null));
			return true;
		}
		return false;
	}

	@Override
	protected float rateDestination(int distance, int x, int y, int z) {
		int points = 0;
		if (isLog(x, y, z)) {
			points++;
		}
		if (isLog(x, y + 1, z)) {
			points++;
		}
		for (int i = 2; i < TREE_HEIGHT; i++) {
			if (!BlockSets.safeSideAndCeilingAround(world, x, y + i, z)) {
				break;
			} else if (isLog(x, y + i, z)) {
				points++;
			}
		}

		return points == 0 ? -1 : distance + 20 - points * 2;
	}

	private boolean isLog(int x, int y, int z) {
		return logs.isAt(world, x, y, z);
	}

	@Override
	protected void addTasksForTarget(BlockPos currentPos) {
		if (handleLargeTree(currentPos)) {
			// This adds one last task to the task queue.
			return;
		}

		int mineAbove = 0;

		for (int i = 2; i < TREE_HEIGHT; i++) {
			if (isLog(currentPos.getX(), currentPos.getY() + i,
					currentPos.getZ())) {
				mineAbove = i;
			}
		}
		int max = 0;
		for (int i = 2; i <= mineAbove; i++) {
			BlockPos pos = currentPos.add(0, i, 0);
			if (!BlockSets.safeSideAndCeilingAround(world, pos)) {
				break;
			}
			if (!BlockSets.AIR.isAt(world, pos)) {
				max = i;
			}
		}
		if (max > 0) {
			addTask(new DestroyInRangeTask(currentPos.add(0, 2, 0),
					currentPos.add(0, max, 0)));
		}

		if (replant) {
			addTask(new PlantSaplingTask(currentPos, type));
		}
		addTask(new WaitTask(mineAbove * 2));
	}

	private boolean handleLargeTree(BlockPos currentPos) {
		for (BlockPos p : new BlockPos[] { currentPos,
				currentPos.add(0, 0, -1), currentPos.add(-1, 0, 0),
				currentPos.add(-1, 0, -1), }) {
			LargeTreeState state = new LargeTreeState(p);
			state.scanTreeHeight(world, currentPos);
			if (state.getTreeHeight() > 3) {
				// we are in a large tree.
				state.setYOffsetByPosition(currentPos);
				addTask(new SwitchToLargeTreeTask(state));
				return true;
			}
		}
		return false;
	}
}
