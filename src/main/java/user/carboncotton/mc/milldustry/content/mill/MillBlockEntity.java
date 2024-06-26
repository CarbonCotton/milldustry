package user.carboncotton.mc.milldustry.content.mill;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MillBlockEntity extends BlockEntity implements Inventory, SidedInventory {

	public static final Map<Item, Integer> FUEL_MAP = AbstractFurnaceBlockEntity.createFuelTimeMap();


	//*** INVENTORY FIELDS ************************************

	private static final int FUEL_SLOT_INDEX = 0;
	private static final int INPUT_SLOT_INDEX = 1;

	private static final int[] FUEL_SLOTS = new int[]{FUEL_SLOT_INDEX};
	private static final int[] INPUT_SLOTS = new int[]{INPUT_SLOT_INDEX};
	private static final int[] OUTPUT_SLOTS = new int[]{2,3,4,5,6,7,8,9};

	private DefaultedList<ItemStack> inventory;


	//*** MILLSTONE FIELDS ************************************

	private static final String MILLING_BLOCK_KEY = "Millstone";
	private ItemStack millingBlock;


	public MillBlockEntity(BlockPos pos, BlockState state) {
		super(AllMillObjects.MILL_BLOCK_ENTITY, pos, state);

		this.inventory = DefaultedList.ofSize(10, ItemStack.EMPTY);

		this.setMillingBlock(ItemStack.EMPTY);
	}


	public static void tick(World world, BlockPos pos, BlockState state, MillBlockEntity blockEntity) {

	}



	//*** NBT INTERFACE ************************************

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);

		Inventories.readNbt(nbt, this.inventory);

		// reading millstone if it exists, otherwise set it to EMPTY
		if(nbt.contains( MillBlockEntity.MILLING_BLOCK_KEY, 10 )) {
			this.setMillingBlock(ItemStack.fromNbt(nbt.getCompound( MillBlockEntity.MILLING_BLOCK_KEY )));
		}
		else {
			this.setMillingBlock(ItemStack.EMPTY);
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);

		Inventories.writeNbt(nbt, this.inventory);

		// store millstone if it exists
		if(!this.getMillingBlock().isEmpty()) {
			nbt.put( MillBlockEntity.MILLING_BLOCK_KEY, this.getMillingBlock().writeNbt(new NbtCompound()) );
		}
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return createNbt();
	}


	@Override
	public void markDirty() {
		super.markDirty();

		if(world != null) {
			this.world.updateListeners(this.getPos(), this.getCachedState(), this.getCachedState(), 3);
		}
	}

	//*** INVENTORY INTERFACE ************************************

	public DefaultedList<ItemStack> getItems() {

		return this.inventory;
	}

	@Override
	public int size() {

		return this.getItems().size();
	}

	@Override
	public boolean isEmpty() {
		for(ItemStack itemStack : this.getItems()) {
			if(!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getStack(int slot) {

		return this.getItems().get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int count) {
		ItemStack result = Inventories.splitStack(this.getItems(), slot, count);

		if(!result.isEmpty()) {
			this.markDirty();
		}

		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {

		return Inventories.removeStack(this.getItems(), slot);
	}


	@Override
	public void setStack(int slot, ItemStack stack) {
		this.getItems().set(slot, stack);

		if(stack.getCount() > stack.getMaxCount()) {
			stack.setCount(stack.getMaxCount());
		}
	}

	@Override
	public void clear() {
		this.getItems().clear();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return true;
	}




	//*** SIDED INVENTORY INTERFACE ******************************


	@Override
	public int[] getAvailableSlots(Direction side) {
		// get front and back of the block
		Direction facingDirection 	= this.getCachedState().get(MillBlock.FACING);
		Direction backDirection 	= facingDirection.getOpposite();

		if(side == facingDirection) {
			return MillBlockEntity.INPUT_SLOTS;
		}

		if(side == backDirection || side == Direction.DOWN) {
			return MillBlockEntity.OUTPUT_SLOTS;
		}

		return MillBlockEntity.FUEL_SLOTS;
	}


	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		//
		if(slot == MillBlockEntity.INPUT_SLOT_INDEX) {
			// get facing of block
			Direction facingDirection = this.getCachedState().get(MillBlock.FACING);

			// we can insert only from front
			return facingDirection == dir;
		}

		// fuel slot
		if(slot == MillBlockEntity.FUEL_SLOT_INDEX) {
			// only fuel is allowed
			if( !MillBlockEntity.FUEL_MAP.containsKey( stack.getItem() ) ) {
				return false;
			}

			// get facing of block
			Direction facingDirection = this.getCachedState().get(MillBlock.FACING);

			// get back of block
			Direction backDirection = facingDirection.getOpposite();

			// can insert into any side except front or back
			return dir != facingDirection && dir != backDirection;
		}

		return false;
	}


	/**
	 * Items can be extracted from following sides:
	 * + bottom of the block (regardless of facing direction)
	 * + back of the block (opposite of facing direction)
	 *
	 * Items can be extracted from slots in range 2 - 9
	 */
	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {

		// you can't extract from those slots
		if(slot == MillBlockEntity.FUEL_SLOT_INDEX || slot == MillBlockEntity.INPUT_SLOT_INDEX) {
			return false;
		}

		// get back of the block
		Direction blockBack = this.getCachedState().get(MillBlock.FACING).getOpposite();

		return dir == Direction.DOWN || dir == blockBack;

	}

	//*** INTERACTION INTERFACE ******************************


	public ItemStack playerInteractedWithSide(ItemStack handItem) {
		// TODO: add fuel handling in future
		var oldMillingBlock = this.getMillingBlock();

		this.setMillingBlock(handItem.copyWithCount(1));
		handItem.decrement(1);

		return oldMillingBlock;
	}




	//*** GETTERS & SETTERS ******************************

	public ItemStack getMillingBlock() {
		return this.millingBlock;
	}

	public void setMillingBlock(ItemStack newMillingBlock) {
		this.millingBlock = newMillingBlock;

		this.markDirty();
	}
}
