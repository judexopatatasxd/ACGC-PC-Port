#include "jaudio_NES/dummyrom.h"

#include "dolphin/ar.h"
#include "jaudio_NES/dvdthread.h"
#include "jaudio_NES/os.h"
#include "jaudio_NES/memory.h"

ALHeap aram_hp;
u8* JAC_ARAM_DMA_BUFFER_TOP = nullptr;
static u32 AUDIO_ARAM_TOP = 0;
static u32 CARD_SECURITY_BUFFER = 0;
static u32 init_load_size = 0;
static u8* init_load_addr = nullptr;
static BOOL init_cut_flag = FALSE;
static u32 SELECTED_ARAM_SIZE = 0;

#ifdef TARGET_PC
#define PC_DMA_QUEUE_SLOTS 128
static OSMesgQueue* pc_dma_queues[PC_DMA_QUEUE_SLOTS];
static u32 pc_dma_next_queue;

static u32 pc_register_dma_queue(OSMesgQueue* queue) {
    for (u32 i = 0; i < PC_DMA_QUEUE_SLOTS; i++) {
        u32 index = (pc_dma_next_queue + i) % PC_DMA_QUEUE_SLOTS;
        if (pc_dma_queues[index] == nullptr) {
            pc_dma_queues[index] = queue;
            pc_dma_next_queue = (index + 1) % PC_DMA_QUEUE_SLOTS;
            return index + 1;
        }
    }
    return 0;
}
#endif

extern u32 GetNeos_FileTop(void) {
    if (init_cut_flag) {
        return 0;
    }

    return init_load_size;
}

extern u32 GetNeosRomTop(void) {
    return AUDIO_ARAM_TOP;
}

extern u32 GetNeosRom_PreLoaded(void) {
    DVDT_DRAMtoARAM(0, (Jac_DVDAddress)(uintptr_t)init_load_addr, AUDIO_ARAM_TOP, init_load_size, nullptr, nullptr);
    return init_load_size;
}

extern u32 SetPreCopy_NeosRom(u8* load_addr, u32 load_size, BOOL cut_flag) {
    init_load_size = load_size;
    init_load_addr = load_addr;
    init_cut_flag = cut_flag;
}

extern void mesg_finishcall(u32 mq) {
#ifdef TARGET_PC
    if (mq != 0 && mq <= PC_DMA_QUEUE_SLOTS) {
        OSMesgQueue* queue = pc_dma_queues[mq - 1];
        pc_dma_queues[mq - 1] = nullptr;
        if (queue != nullptr) {
            Z_osSendMesg(queue, NULL, OS_MESSAGE_NOBLOCK);
        }
    }
#else
    Z_osSendMesg((OSMesgQueue*)mq, NULL, OS_MESSAGE_NOBLOCK);
#endif
}

extern BOOL ARAMStartDMAmesg(u32 dir, uintptr_t dramAddr, u32 aramAddr, u32 size, s32 unused, OSMesgQueue* mq) {
    aramAddr += AUDIO_ARAM_TOP;

#ifdef TARGET_PC
    u32 owner = pc_register_dma_queue(mq);
#else
    u32 owner = (u32)mq;
#endif

    if (dir == DUMMYROM_ARAM_TO_DRAM) {
        DVDT_ARAMtoDRAM(owner, (Jac_DVDAddress)dramAddr, aramAddr, size, nullptr, &mesg_finishcall);
    } else {
        DVDT_DRAMtoARAM(owner, (Jac_DVDAddress)dramAddr, aramAddr, size, nullptr, &mesg_finishcall);
    }

    return FALSE;
}

extern void Jac_SetAudioARAMSize(u32 size) {
    SELECTED_ARAM_SIZE = size;
}

extern void* ARAllocFull(u32* outSize) {
    u32 freeSize = aram_hp.length - (u32)((uintptr_t)aram_hp.current - (uintptr_t)aram_hp.base);
    void* alloc = Nas_HeapAlloc(&aram_hp, freeSize - 32);
    *outSize = freeSize - 32;
    return alloc;
}

extern void Jac_InitARAM(u32 loadAudiorom) {
    u32 aram_size = AUDIO_ARAM_SIZE;
    volatile u32 audiorom_size;

    if (SELECTED_ARAM_SIZE != 0) {
        aram_size = SELECTED_ARAM_SIZE;
    }

    AUDIO_ARAM_TOP = ARGetBaseAddress();
    if (loadAudiorom) {
        audiorom_size = Jac_CheckFile("/audiorom.img");
        if (audiorom_size != 0) {
            audiorom_size = ALIGN_NEXT(audiorom_size, 32);
            (void)audiorom_size; /* leftover from some debug print? */
        }
    } else {
        audiorom_size = 0;
    }

    CARD_SECURITY_BUFFER = 0x40;
    audiorom_size += AUDIO_ARAM_TOP;
    JAC_ARAM_DMA_BUFFER_TOP = (u8*)audiorom_size;
    audiorom_size += AUDIO_ARAM_HEAP_SIZE;
    Nas_HeapInit(&aram_hp, (u8*)audiorom_size, aram_size - audiorom_size);

    /* Probably leftovers from some debug print statement */
    (void)audiorom_size;
    (void)audiorom_size;
}
