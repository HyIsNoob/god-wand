package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

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
) implements CustomPayload {

    public static final CustomPayload.Id<CinematicFxPayload> ID = new CustomPayload.Id<>(Identifier.of("godwand", "cinematic_fx"));

    // Các sự kiện điện ảnh đồng bộ với kỹ năng
    public static final int EVENT_TRAUMA_SHAKE        = 0; // Rung chấn thông thường
    public static final int EVENT_FOV_PUNCH           = 1; // Giật mạnh FOV
    public static final int EVENT_VACUUM_SILENCE      = 2; // Khoảng lặng chân không (tắt tiếng mob/môi trường)
    public static final int EVENT_METEOR_FOCUS        = 3; // Nén lực thiên thạch Chiêu 3 (FOV 1.0 -> 0.88)
    public static final int EVENT_METEOR_SLAM         = 4; // Thiên thạch cắm đất Chiêu 3 (Bung FOV 1.25 + Trauma 0.85)
    public static final int EVENT_SUPERNOVA_COMPRESS  = 5; // Sụp đổ hấp dẫn Chiêu 4 (Rung nhịp tim + FOV dao động)
    public static final int EVENT_SUPERNOVA_BURST     = 6; // Đại nổ Siêu Tân Tinh Chiêu 4 (Max Trauma 1.0 + Bung FOV 1.32)

    public static final PacketCodec<RegistryByteBuf, CinematicFxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, CinematicFxPayload::eventType,
            PacketCodecs.DOUBLE, CinematicFxPayload::x,
            PacketCodecs.DOUBLE, CinematicFxPayload::y,
            PacketCodecs.DOUBLE, CinematicFxPayload::z,
            PacketCodecs.FLOAT, CinematicFxPayload::intensity,
            PacketCodecs.INTEGER, CinematicFxPayload::durationTicks,
            CinematicFxPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
