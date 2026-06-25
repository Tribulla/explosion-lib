package com.example.explosionlib.item;

import com.example.explosionlib.platform.ClientPlatform;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class ExploderItem extends Item {
    public ExploderItem(Properties props) {
        super(props); // 1.21.1: do NOT call Properties#setId — id comes from the registration ResourceLocation
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && player.isShiftKeyDown()) {
            ClientPlatform.INSTANCE.openExploderConfigScreen();
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (ctx.getLevel().isClientSide && player != null && player.isShiftKeyDown()) {
            ClientPlatform.INSTANCE.openExploderConfigScreen();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
