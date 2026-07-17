/* ARM64 decoder for the original packed (32-bit, big-endian) audio banks.
 *
 * The GameCube bank format stores pointers as u32 offsets. Native audio
 * structures contain real pointers, so overlaying the packed bytes only works
 * in a 32-bit process. Decode the packed records into native structures and
 * leave the source bank bytes untouched except for scalar envelope/book/loop
 * data, which retain the same layout on both ABIs. */

typedef struct PcBank64WaveRef {
    u32 offset;
    smzwavetable* value;
} PcBank64WaveRef;

typedef struct PcBank64Context {
    u8* base;
    size_t size;
    WaveMedia* media;
    PcBank64WaveRef* waves;
    size_t wave_count;
    size_t wave_capacity;
} PcBank64Context;

static BOOL pc_bank64_range(const PcBank64Context* ctx, u32 offset, size_t size) {
    return offset <= ctx->size && size <= ctx->size - offset;
}

static u32 pc_bank64_u32(const u8* ptr) {
    u32 value;
    memcpy(&value, ptr, sizeof(value));
    return BSWAP32(value);
}

static f32 pc_bank64_f32(const u8* ptr) {
    u32 bits = pc_bank64_u32(ptr);
    f32 value;
    memcpy(&value, &bits, sizeof(value));
    return value;
}

static void* pc_bank64_alloc(size_t count, size_t size) {
    if (count == 0 || size == 0 || count > SIZE_MAX / size) {
        return NULL;
    }
    return calloc(count, size);
}

static smzwavetable* pc_bank64_wave(PcBank64Context* ctx, u32 offset) {
    if (offset == 0 || !pc_bank64_range(ctx, offset, 0x10)) {
        return NULL;
    }

    for (size_t i = 0; i < ctx->wave_count; i++) {
        if (ctx->waves[i].offset == offset) {
            return ctx->waves[i].value;
        }
    }

    smzwavetable* wave = (smzwavetable*)pc_bank64_alloc(1, sizeof(*wave));
    if (wave == NULL) {
        return NULL;
    }

    if (ctx->wave_count < ctx->wave_capacity) {
        ctx->waves[ctx->wave_count].offset = offset;
        ctx->waves[ctx->wave_count].value = wave;
        ctx->wave_count++;
    }

    const u8* raw = ctx->base + offset;
    u32 header = pc_bank64_u32(raw);
    u32 sample_offset = pc_bank64_u32(raw + 4);
    u32 loop_offset = pc_bank64_u32(raw + 8);
    u32 book_offset = pc_bank64_u32(raw + 12);

    wave->size = header & 0x00ffffff;
    wave->is_relocated = (header >> 24) & 1;
    wave->bit26 = (header >> 25) & 1;
    wave->medium = (header >> 26) & 3;
    wave->codec = (header >> 28) & 7;
    wave->bit31 = (header >> 31) & 1;

    if (wave->size != 0) {
        if (loop_offset != 0 && pc_bank64_range(ctx, loop_offset, sizeof(adpcmloop))) {
            wave->loop = (adpcmloop*)(ctx->base + loop_offset);
            pc_swap_adpcmloop(wave->loop);
        }
        if (book_offset != 0 && pc_bank64_range(ctx, book_offset, sizeof(adpcmbook))) {
            wave->book = (adpcmbook*)(ctx->base + book_offset);
            pc_swap_adpcmbook(wave->book);
        }

        switch (wave->medium) {
            case MEDIUM_RAM:
                wave->sample = (u8*)((uintptr_t)ctx->media->wave0_p + sample_offset);
                wave->medium = ctx->media->wave0_media;
                break;
            case MEDIUM_DISK:
                wave->sample = (u8*)((uintptr_t)ctx->media->wave1_p + sample_offset);
                wave->medium = ctx->media->wave1_media;
                break;
            default:
                wave->sample = (u8*)(uintptr_t)sample_offset;
                break;
        }

        wave->is_relocated = TRUE;
        if (wave->bit26 && wave->medium != MEDIUM_RAM &&
            AG.num_used_samples < ARRAY_COUNT(AG.used_samples)) {
            AG.used_samples[AG.num_used_samples++] = wave;
        }
    }

    return wave;
}

static void pc_bank64_wtstr(PcBank64Context* ctx, wtstr* out, u32 offset) {
    if (!pc_bank64_range(ctx, offset, 8)) {
        return;
    }
    out->wavetable = pc_bank64_wave(ctx, pc_bank64_u32(ctx->base + offset));
    out->tuning = pc_bank64_f32(ctx->base + offset + 4);
}

static envdat* pc_bank64_envelope(PcBank64Context* ctx, u32 offset) {
    if (offset == 0 || !pc_bank64_range(ctx, offset, sizeof(envdat))) {
        return NULL;
    }
    envdat* envelope = (envdat*)(ctx->base + offset);
    pc_swap_envdat(envelope);
    return envelope;
}

static voicetable* pc_bank64_voice(PcBank64Context* ctx, u32 offset) {
    if (offset == 0 || !pc_bank64_range(ctx, offset, 0x20)) {
        return NULL;
    }

    const u8* raw = ctx->base + offset;
    voicetable* voice = (voicetable*)pc_bank64_alloc(1, sizeof(*voice));
    if (voice == NULL) {
        return NULL;
    }

    voice->is_relocated = TRUE;
    voice->normal_range_low = raw[1];
    voice->normal_range_high = raw[2];
    voice->adsr_decay_idx = raw[3];
    voice->envelope = pc_bank64_envelope(ctx, pc_bank64_u32(raw + 4));
    if (voice->normal_range_low != 0) {
        pc_bank64_wtstr(ctx, &voice->low_pitch_tuned_sample, offset + 8);
    }
    pc_bank64_wtstr(ctx, &voice->normal_pitch_tuned_sample, offset + 0x10);
    if (voice->normal_range_high != 0x7f) {
        pc_bank64_wtstr(ctx, &voice->high_pitch_tuned_sample, offset + 0x18);
    }
    return voice;
}

static perctable* pc_bank64_percussion(PcBank64Context* ctx, u32 offset) {
    if (offset == 0 || !pc_bank64_range(ctx, offset, 0x10)) {
        return NULL;
    }

    const u8* raw = ctx->base + offset;
    perctable* percussion = (perctable*)pc_bank64_alloc(1, sizeof(*percussion));
    if (percussion == NULL) {
        return NULL;
    }

    percussion->adsr_decay_idx = raw[0];
    percussion->pan = raw[1];
    percussion->is_relocated = TRUE;
    pc_bank64_wtstr(ctx, &percussion->tuned_sample, offset + 4);
    percussion->envelope = pc_bank64_envelope(ctx, pc_bank64_u32(raw + 0x0c));
    return percussion;
}

static void pc_bank64_decode(s32 bank_id, u8* ctrl_p, WaveMedia* media) {
    voiceinfo* info = &AG.voice_info[bank_id];
    size_t raw_size = (size_t)AG.bank_header->entries[bank_id].size;
    if (AG.bank_header->entries[bank_id].medium == MEDIUM_RAM_UNLOADED && raw_size >= sizeof(ArcEntry)) {
        raw_size -= sizeof(ArcEntry);
    }

    PcBank64Context ctx = {};
    ctx.base = ctrl_p;
    ctx.size = raw_size;
    ctx.media = media;
    ctx.wave_capacity = 8 + info->num_drums + info->num_sfx + (size_t)info->num_instruments * 3;
    ctx.waves = (PcBank64WaveRef*)pc_bank64_alloc(ctx.wave_capacity, sizeof(*ctx.waves));

    u32 percussion_table = pc_bank64_range(&ctx, 0, 4) ? pc_bank64_u32(ctrl_p) : 0;
    u32 effects_table = pc_bank64_range(&ctx, 4, 4) ? pc_bank64_u32(ctrl_p + 4) : 0;

    info->percussion = (perctable**)pc_bank64_alloc(info->num_drums, sizeof(*info->percussion));
    if (info->percussion != NULL && percussion_table != 0 &&
        pc_bank64_range(&ctx, percussion_table, (size_t)info->num_drums * 4)) {
        for (u32 i = 0; i < info->num_drums; i++) {
            u32 offset = pc_bank64_u32(ctrl_p + percussion_table + i * 4);
            info->percussion[i] = pc_bank64_percussion(&ctx, offset);
        }
    }

    info->effects = (percvoicetable*)pc_bank64_alloc(info->num_sfx, sizeof(*info->effects));
    if (info->effects != NULL && effects_table != 0 &&
        pc_bank64_range(&ctx, effects_table, (size_t)info->num_sfx * 8)) {
        for (u32 i = 0; i < info->num_sfx; i++) {
            pc_bank64_wtstr(&ctx, &info->effects[i].tuned_sample, effects_table + i * 8);
        }
    }

    info->instruments = (voicetable**)pc_bank64_alloc(info->num_instruments, sizeof(*info->instruments));
    if (info->instruments != NULL && pc_bank64_range(&ctx, 8, (size_t)info->num_instruments * 4)) {
        for (u32 i = 0; i < info->num_instruments; i++) {
            u32 offset = pc_bank64_u32(ctrl_p + 8 + i * 4);
            info->instruments[i] = pc_bank64_voice(&ctx, offset);
        }
    }

    free(ctx.waves);
}
