package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
/** Private visual receipt for the exact item actually delivered, including its components. */
public record VillagerGiftPayload(UUID receipt,UUID villager,Identifier dimension,ItemStack item) implements CustomPayload {
 public VillagerGiftPayload {item=item.copy();if(item.isEmpty())throw new IllegalArgumentException("Empty gift receipt");}
 public static final Id<VillagerGiftPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","villager_gift"));
 public static final PacketCodec<RegistryByteBuf,VillagerGiftPayload> CODEC=PacketCodec.tuple(
  Uuids.PACKET_CODEC,VillagerGiftPayload::receipt,Uuids.PACKET_CODEC,VillagerGiftPayload::villager,
  Identifier.PACKET_CODEC,VillagerGiftPayload::dimension,ItemStack.PACKET_CODEC,VillagerGiftPayload::item,VillagerGiftPayload::new);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
