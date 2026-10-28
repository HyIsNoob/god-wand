package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload đồng bộ các sự kiện điện ảnh từ Server xuống Client:
 * - Trauma Screen Shake
 * - Dynamic FOV Zoom (Co/Bung)
 * - Vacuum Silence (Tắt âm thanh môi trường trước vụ nổ)
 */
public record CinematicFxPayload(
        int eventType,
        double x,
        double y,
        double z,
        float intensity,
        int durationTicks
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CinematicFxPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("godwand", "cinematic_fx"));

    // Các sự kiện điện ảnh đồng bộ với kỹ năng
    public static final int EVENT_TRAUMA_SHAKE        = 0; // Rung chấn thông thường
    public static final int EVENT_FOV_PUNCH           = 1; // Giật mạnh FOV
    public static final int EVENT_VACUUM_SILENCE      = 2; // Khoảng lặng chân không (tắt tiếng mob/môi trường)
    public static final int EVENT_METEOR_FOCUS        = 3; // Nén lực thiên thạch Chiêu 3 (FOV 1.0 -> 0.88)
    public static final int EVENT_METEOR_SLAM         = 4; // Thiên thạch cắm đất Chiêu 3 (Bung FOV 1.25 + Trauma 0.85)
    public static final int EVENT_SUPERNOVA_COMPRESS  = 5; // Sụp đổ hấp dẫn Chiêu 4 (Rung nhịp tim + FOV dao động)
    public static final int EVENT_SUPERNOVA_BURST     = 6; // Đại nổ Siêu Tân Tinh Chiêu 4 (Max Trauma 1.0 + Bung FOV 1.32)

    public static final StreamCodec<RegistryFriendlyByteBuf, CinematicFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CinematicFxPayload::eventType,
            ByteBufCodecs.DOUBLE, CinematicFxPayload::x,
            ByteBufCodecs.DOUBLE, CinematicFxPayload::y,
            ByteBufCodecs.DOUBLE, CinematicFxPayload::z,
            ByteBufCodecs.FLOAT, CinematicFxPayload::intensity,
            ByteBufCodecs.INT, CinematicFxPayload::durationTicks,
            CinematicFxPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
