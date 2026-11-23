# HVM_* 환경변수 (네이티브에서 getenv 하는 것 전부)

설정 방법: 앱 '추가 환경변수' 입력란(K=V) 또는 `adb shell am start -n io.horizonvm.app/.MainActivity --es env 'HVM_X=1;HVM_Y=2' ...`

| 변수 | 읽는 함수 |
|---|---|
| `HVM_6GB_APPLETDEV` | hvm::run_app |
| `HVM_A32_NO_FASTMEM` | hvm::DbtX64Backend::run_a32 |
| `HVM_ADSP_DIS` | hvm::AdspCore::run |
| `HVM_ADSP_DUMP` | hvm::Ape::tick |
| `HVM_ADSP_IDLE_US` | hvm::Ape::tick |
| `HVM_ADSP_SAFE` | hvm::AdspCore::AdspCore |
| `HVM_ADSP_SPIN` | hvm::Ape::tick |
| `HVM_ADSP_STEP` | hvm::Ape::hwmbox_written |
| `HVM_ADSP_TSCDBG` | hvm::Ape::reg_read |
| `HVM_APBDMA_TRACE` | hvm::TegraApbDma::process |
| `HVM_APETRACE` | hvm::AdspCore::pread, hvm::AdspCore::run, hvm::Ape::hwmbox_written, hvm::Ape::reg_read, hvm::Ape::reg_write, hvm::Ape::tick |
| `HVM_AUTO_CAPTURE` | hvm::gfx::install_eden_renderer |
| `HVM_BAR1LOG` | hvm::Gm20bBar1::write |
| `HVM_BARRIER_CTL` | hvm::gfx::vk::Device::transition |
| `HVM_BIGCORE` | hvm::Machine::steer_host_cores |
| `HVM_BROAD_BARRIERS` | hvm::gfx::vk::Device::Device |
| `HVM_BT_TRACE` | hvm::BtHciPeer::BtHciPeer |
| `HVM_CAPTURE_REPEAT` | hvm::DbtX64Backend::DbtX64Backend, hvm::evt::Ring::load_env |
| `HVM_CBC_PARAM` | hvm::MachineHac001::build |
| `HVM_CLOCK_IDENTITY` | hvm::VirtualClock::configure |
| `HVM_DBT_ACCURATE_FP` | hvm::DbtX64Backend::make_ctx |
| `HVM_DBT_INLINE_EXCL` | hvm::DbtX64Backend::make_ctx |
| `HVM_DBT_NODIRECTPS` | hvm::DbtX64Backend::make_ctx |
| `HVM_DBT_NODIRECTSYS` | hvm::DbtX64Backend::make_ctx |
| `HVM_DBT_NOFASTSYS` | hvm::DbtX64Backend::make_ctx |
| `HVM_DBT_NOGSE` | hvm::DbtX64Backend::make_ctx |
| `HVM_DBT_NOOPT` | hvm::DbtX64Backend::make_ctx, hvm::DbtX64Backend::make_ctx32 |
| `HVM_DBT_NOREVAL` | hvm::DbtX64Backend::DbtX64Backend |
| `HVM_DBT_PSTATS` | hvm::DbtX64Backend::run |
| `HVM_DC_HOOK_ALL` | hvm::DbtX64Backend::make_ctx |
| `HVM_DC_PRESENT_ALL` | hvm::T210Dc::same_present |
| `HVM_DC_TRACE` | hvm::T210Dc::scanout, hvm::T210Dc::vblank, hvm::T210Dc::write |
| `HVM_DMA_STATS` | hvm::DmaPort::to_gpa, hvm::Gm20bBar1::mmu_invalidate, hvm::Gm20bBar1::translate |
| `HVM_DSTG0` | hvm::MachineHac001::build |
| `HVM_DUMP_CPUS` | hvm::run_app |
| `HVM_DUMP_PA` | hvm::run_app |
| `HVM_DUMP_VA` | hvm::run_app |
| `HVM_EARLY_FENCE_DEPTH` | hvm::Gm20bFifo::Gm20bFifo |
| `HVM_EDEN_ACC` | hvm::eden::Bridge::create |
| `HVM_EDEN_ASYNC_SHADERS` | hvm::eden::Bridge::create |
| `HVM_EDEN_CACHE_ID` | hvm::eden::Bridge::create |
| `HVM_EDEN_DEFER_CLEAR` | Vulkan::RasterizerVulkan::Clear |
| `HVM_EDEN_FENCE` | hvm::eden::Bridge::create |
| `HVM_EDEN_FORCE_XFB_BARRIER` | Vulkan::Scheduler::Scheduler |
| `HVM_EDEN_FULL_MAP` | hvm::Gm20bFifo::eden_prepare |
| `HVM_EDEN_GLOBAL_SYNC_GEN` | VideoCommon::BufferCache<Vulkan::BufferCacheParams>::SynchronizeBuffer |
| `HVM_EDEN_GPU` | hvm::gfx::install_eden_renderer |
| `HVM_EDEN_LEGACY_FRAGMENT_BARRIER` | Vulkan::Scheduler::Scheduler |
| `HVM_EDEN_LEGACY_QUERY_SYNC` | Vulkan::QueryCacheRuntimeImpl::QueryCacheRuntimeImpl |
| `HVM_EDEN_LOG` | hvm::eden::Bridge::create |
| `HVM_EDEN_MAPCHECK` | hvm::Gm20bFifo::eden_prepare |
| `HVM_EDEN_MAPVERIFY` | hvm::Gm20bFifo::eden_prepare |
| `HVM_EDEN_NO_BCN_PATCH` | hvm::eden::Bridge::create |
| `HVM_EDEN_NO_DISK_CACHE` | hvm::eden::Bridge::create |
| `HVM_EDEN_NO_DLFILTER` | Tegra::MaxwellDeviceMemoryManager::WriteBlockUnsafe |
| `HVM_EDEN_NO_DRAW_REUSE` | Tegra::MemoryManager::HvmReadDescriptorWord, VideoCommon::BufferCache<Vulkan::BufferCacheParams>::BindHostVertexBuffers, Vulkan::BufferCacheRuntime::BindIndexBuffer |
| `HVM_EDEN_NO_RESCUE` | hvm::Gm20bFifo::quiet_dump |
| `HVM_EDEN_NO_RUN` | hvm::Gm20bFifo::run_channel |
| `HVM_EDEN_NO_SYNC_SKIP` | VideoCommon::BufferCache<Vulkan::BufferCacheParams>::BindHostVertexBuffers, VideoCommon::BufferCache<Vulkan::BufferCacheParams>::SynchronizeBuffer |
| `HVM_EDEN_ORDERED_UPLOADS` | Vulkan::BufferCacheRuntime::CanReorderUpload |
| `HVM_EDEN_OUTPUT_SCALE` | hvm::eden::EdenPresenter::EdenPresenter |
| `HVM_EDEN_PRESENT` | hvm::SdlFrameSink::create, hvm::eden::Bridge::create |
| `HVM_EDEN_PRESENT_ALL` | hvm::eden::EdenPresenter::present |
| `HVM_EDEN_PRESENT_FIFO` | hvm::eden::EdenPresenter::create_swapchain |
| `HVM_EDEN_QMDCHECK` | hvm::Gm20bFifo::eden_call |
| `HVM_EDEN_REACTIVE` | hvm::gfx::install_eden_renderer |
| `HVM_EDEN_REC_SEC` | hvm::Gm20bFifo::eden_rec |
| `HVM_EDEN_RENDER_SCALE` | hvm::eden::Bridge::create |
| `HVM_EDEN_SKIP_PACKED10` | Vulkan::Image::UploadMemory |
| `HVM_EDEN_SPLIT_ACTIVE_PASS` | Vulkan::RasterizerVulkan::FlushWork |
| `HVM_EDEN_SPLIT_CLEAR_PASS` | Vulkan::Scheduler::RequestRenderpass |
| `HVM_EDEN_VK_DEBUG` | hvm::eden::Bridge::create |
| `HVM_EVT` | hvm::evt::Ring::load_env, hvm::evt::ring, hvm::gfx::install_eden_renderer |
| `HVM_EVT_FROM` | hvm::evt::Ring::load_env |
| `HVM_EVT_GPU_ONLY` | hvm::evt::rec |
| `HVM_EVT_STREAM` | hvm::evt::rec |
| `HVM_EVT_TO` | hvm::evt::Ring::load_env |
| `HVM_EVT_TRIG` | hvm::evt::Ring::load_env, hvm::trigger_capture |
| `HVM_FATAL_DUMP` | hvm::hztrace::on_exception |
| `HVM_FATAL_DUMP_BASE` | hvm::hztrace::on_exception |
| `HVM_FATAL_DUMP_SIZE` | hvm::hztrace::on_exception |
| `HVM_FIFOLOG` | hvm::Gm20bFifo::Gm20bFifo |
| `HVM_FIND` | hvm::run_app |
| `HVM_FORCE_WINDOW` | hvm::ShadowMapper::init |
| `HVM_FREEZE_WATCH` | hvm::Machine::watch_freeze |
| `HVM_GIC_STATS` | hvm::Gic400::Gic400 |
| `HVM_GPIO_IN` | hvm::MachineHac001::build |
| `HVM_GPU` | hvm::SdlFrameSink::create, hvm::gfx::eden_requested, hvm::gfx::install_eden_renderer, hvm::gfx::install_renderer |
| `HVM_GPU3D_TRACE` | hvm::Fermi2D::blit, hvm::Maxwell3D::Maxwell3D, hvm::MaxwellDma::launch |
| `HVM_GPUREG_TRACE` | hvm::MachineHac001::build |
| `HVM_GPU_SPLIT` | hvm::gfx::wrap_async_sink |
| `HVM_GPU_THREAD` | hvm::Machine::Machine |
| `HVM_GPU_TS` | Vulkan::Scheduler::Scheduler |
| `HVM_GPU_TS_FULL` | Vulkan::Scheduler::Scheduler |
| `HVM_GSAMP` | hvm::DbtX64Backend::DbtX64Backend, hvm::DbtX64Backend::~DbtX64Backend, hvm::NceSession::start, hvm::hostsamp::register_this_thread, hvm::sampler::start_from_env |
| `HVM_GSAMP_OUT` | hvm::DbtX64Backend::gsamp_dump |
| `HVM_GSAMP_TRIG` | hvm::trigger_capture |
| `HVM_HDA_TRACE` | hvm::Hda::Hda |
| `HVM_HOST1X_TRACE` | hvm::Host1x::Host1x |
| `HVM_HOST_SYNC` | hvm::Gm20bFifo::Gm20bFifo |
| `HVM_HZTRACE` | hvm::hztrace::init_from_env |
| `HVM_IGNORE_CLOCK_GATING` | hvm::run_app |
| `HVM_IMU_WHOAMI` | hvm::Lsm6ds3::Lsm6ds3 |
| `HVM_IPC_DUMP` | hvm::hztrace::on_exception |
| `HVM_IPC_RAW` | hvm::hztrace::on_exception |
| `HVM_IPC_STUCK` | hvm::hztrace::on_exception |
| `HVM_JC_DELAY_US` | hvm::JoyConRail::send_raw |
| `HVM_JC_TRACE` | hvm::JoyConRail::JoyConRail |
| `HVM_JIT_EL0_CACHE_MB` | hvm::DbtX64Backend::make_ctx |
| `HVM_KEEP_AWAKE` | hvm::run_app |
| `HVM_KEEP_AWAKE_XY` | hvm::run_app |
| `HVM_LOG_BUFFERED` | hvm::log::write |
| `HVM_LOG_RECENT_ALL` | hvm::log::enabled, hvm::log::write |
| `HVM_LTC` | hvm::MachineHac001::build |
| `HVM_MON` | hvm::VCpu::wait_for_interrupt, hvm::create_and_boot, hvm::eden::Bridge::create, hvm::gfx::vk::Device::submit_wait, hvm::gfx::vk::Device::wait_slot, hvm::run_app |
| `HVM_MON_SEC` | hvm::run_app |
| `HVM_NCE` | hvm::DbtX64Backend::DbtX64Backend, hvm::VirtualClock::configure |
| `HVM_NCE_ASID` | hvm::NceSession::NceSession |
| `HVM_NCE_CHECK` | hvm::NceSession::check_locked |
| `HVM_NCE_CNT_SLEEP_US` | hvm::NceSession::start |
| `HVM_NCE_HEAL` | hvm::NceSession::report_samples |
| `HVM_NCE_NOPIN` | hvm::NceSession::report |
| `HVM_NCE_NOPIN_SELF` | hvm::NceSession::run |
| `HVM_NCE_NOSKIP` | hvm::NceSession::revalidate_locked |
| `HVM_NCE_NO_BACKOFF` | hvm::NceSession::start |
| `HVM_NCE_PROTECT_ASYNC` | hvm::NceSession::on_protect_batch, hvm::NceSession::on_resident |
| `HVM_NCE_PSPIN_US` | hvm::NceSession::run |
| `HVM_NCE_REVAL_LOCAL` | hvm::NceSession::record_grant, hvm::NceSession::revalidate_locked |
| `HVM_NCE_SAME_CORE_SPIN` | hvm::NceSession::run |
| `HVM_NCE_SAMP_US` | hvm::NceSession::start |
| `HVM_NCE_SPINPAUSE` | hvm::NceSession::run |
| `HVM_NCE_SPIN_YIELD_US` | hvm::NceSession::start |
| `HVM_NCE_SPLIT` | hvm::NceSession::report |
| `HVM_NCE_SYNC_RECLAIM` | hvm::NceSession::start |
| `HVM_NCE_TLBI_ASYNC` | hvm::NceSession::try_defer_tlbi |
| `HVM_NCE_VA_BITS` | hvm::NceSession::NceSession |
| `HVM_NCE_WSPIN_US` | hvm::NceSession::start |
| `HVM_NCE_YIELDSW_US` | hvm::NceSession::run |
| `HVM_NET_HOSTACCESS` | hvm::Bcm4356::reset |
| `HVM_NO_ADSP` | hvm::Ape::tick |
| `HVM_NO_AGIC_CACHE` | hvm::Ape::best_pending |
| `HVM_NO_AROUND` | hvm::ShadowMapper::on_host_fault_impl |
| `HVM_NO_AUDIO` | hvm::audio::enabled |
| `HVM_NO_BIGTLB` | hvm::Gm20bBar1::translate |
| `HVM_NO_CAL0_FIX` | hvm::fix_cal0_wlan_country |
| `HVM_NO_EARLY_FENCE` | hvm::Gm20bFifo::Gm20bFifo |
| `HVM_NO_FASTMEM` | hvm::ShadowMapper::init |
| `HVM_NO_FASTMEM_WINDOW` | hvm::ShadowMapper::init_window |
| `HVM_NO_FMTLIST` | hvm::gfx::vk::Device::create_image |
| `HVM_NO_GTLB` | hvm::Gm20bBar1::translate |
| `HVM_NO_MMIO_REPORT` | hvm::PhysBus::mmio_report |
| `HVM_NO_PAGE_STAMP` | hvm::Gm20bFifo::ChanVm::vm_stamp |
| `HVM_NO_PASS_MERGE` | hvm::gfx::vk::Device::cmd_pass |
| `HVM_NO_PTDIFF` | hvm::ShadowMapper::ShadowMapper |
| `HVM_NO_PTTRACK` | hvm::ShadowMapper::ShadowMapper, hvm::SwTlb::SwTlb |
| `HVM_NO_PT_WATCH` | hvm::Gm20bBar1::Gm20bBar1 |
| `HVM_NO_RECYCLER` | hvm::gfx::vk::Device::start_recycler |
| `HVM_NO_SD_SETTINGS` | hvm::ensure_sd_system_settings |
| `HVM_NO_SIGCACHE` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_NO_SIGPART` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_NO_SIG_RETL` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_NO_SIG_SINGLE` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_NO_SWTLB` | hvm::SwTlb::init |
| `HVM_NO_TLSIG` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_NO_VGEN` | hvm::Gm20bBar1::invalidate_range |
| `HVM_NO_VK_REC` | hvm::gfx::vk::cmdrec::start |
| `HVM_PAD_CMD` | hvm::run_app |
| `HVM_PCIETRACE` | hvm::TegraPcie::cfg_read, hvm::TegraPcie::cfg_write |
| `HVM_PCLOG` | hvm::VCpu::handle_exit |
| `HVM_PCLOG_DUMP` | hvm::VCpu::handle_exit |
| `HVM_PCLOG_FP` | hvm::VCpu::handle_exit |
| `HVM_PD_ALERT` | hvm::Bm92t36::stop |
| `HVM_PD_RESET_ALERT` | hvm::Bm92t36::reset_pin |
| `HVM_PLA_NULL_TRACE` | hvm::hztrace::on_exception |
| `HVM_PRESENT` | hvm::SdlFrameSink::create, hvm::T210Dc::realize |
| `HVM_PRESENT_INFLIGHT` | hvm::gfx::vk::Presenter::create_static |
| `HVM_PRESENT_SYNC` | hvm::gfx::vk::Device::submit_present |
| `HVM_PRESENT_WAIT_ALL` | hvm::gfx::vk::Presenter::present |
| `HVM_PROF` | hvm::DbtX64Backend::DbtX64Backend, hvm::sampler::start_from_env |
| `HVM_PROF_AFTER` | hvm::sampler::start_from_env |
| `HVM_PROF_ALL_THREADS` | hvm::sampler::start_from_env |
| `HVM_PROF_CALLER` | hvm::sampler::start_from_env |
| `HVM_PROF_RAW` | hvm::sampler::start_from_env, hvm::sampler::stop_and_report |
| `HVM_PROF_THREAD` | hvm::sampler::start_from_env |
| `HVM_PTDIFF_ALL` | hvm::ShadowMapper::ShadowMapper |
| `HVM_PT_PROTECT` | hvm::Gm20bBar1::mmu_invalidate, hvm::Gm20bBar1::pt_protect |
| `HVM_REC_SPIN_US` | hvm::gfx::vk::cmdrec::Ring::consume |
| `HVM_REVAL_SLOW` | hvm::DbtX64Backend::revalidate_ctx |
| `HVM_RTSTATS` | hvm::T210Dc::vblank |
| `HVM_SAMPLE` | hvm::DbtX64Backend::DbtX64Backend, hvm::DbtX64Backend::~DbtX64Backend |
| `HVM_SCANDBG` | hvm::T210Dc::try_direct |
| `HVM_SD_COW` | hvm::MachineHac001::attach_storage |
| `HVM_SHADER_DIR` | hvm::eden::Bridge::create |
| `HVM_SHDISP_ALL` | hvm::shader::ir::translate |
| `HVM_SHSTATS` | hvm::run_app |
| `HVM_SIG_LAP` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_SIG_SITES` | hvm::Gm20bFifo::ChanVm::page_sig_impl<false>, hvm::Gm20bFifo::ChanVm::page_sig_impl<true> |
| `HVM_SLOWWR` | hvm::DbtX64Backend::Callbacks::MemoryRead16, hvm::DbtX64Backend::Callbacks::MemoryRead32, hvm::DbtX64Backend::Callbacks::MemoryRead64, hvm::DbtX64Backend::Callbacks::MemoryRead8, hvm::DbtX64Backend::C |
| `HVM_SMCTRACE` | hvm::VCpu::handle_exit |
| `HVM_SMMU_NO_RANGE` | hvm::DmaPort::to_gpa |
| `HVM_SMMU_PTC_EPOCH` | hvm::T210Smmu::reg_write |
| `HVM_SOFTGPU` | hvm::gfx::install_renderer, hvm::gfx::install_soft_renderer |
| `HVM_SPAN` | hvm::DirtyMap::DirtyMap, hvm::Gm20bFifo::Gm20bFifo |
| `HVM_SPAN_VERIFY` | hvm::DirtyMap::DirtyMap, hvm::Gm20bFifo::Gm20bFifo |
| `HVM_SPIN_US` | hvm::Machine::event_loop |
| `HVM_SPI_TRACE` | hvm::TegraSpi::read, hvm::TegraSpi::transfer, hvm::TegraSpi::write |
| `HVM_SVCLR_DUMP` | hvm::DbtX64Backend::run |
| `HVM_SVCSTAT` | hvm::hztrace::init_from_env, hvm::hztrace::svc_stat |
| `HVM_SVC_BT` | hvm::hztrace::on_exception |
| `HVM_SVC_DUMP_AFTER` | hvm::hztrace::on_exception |
| `HVM_SVC_DUMP_PTR` | hvm::hztrace::on_exception |
| `HVM_SVC_DUMP_REL` | hvm::hztrace::on_exception |
| `HVM_SVC_DUMP_SKIP` | hvm::hztrace::on_exception |
| `HVM_SVC_LONGWAIT` | hvm::hztrace::on_exception |
| `HVM_SVC_NODEDUMP` | hvm::hztrace::on_exception |
| `HVM_SVC_STKSCAN` | hvm::hztrace::on_exception |
| `HVM_SVC_WATCHSTATE` | hvm::hztrace::on_exception |
| `HVM_SYNC_HOST_WB` | hvm::Gm20bFifo::Gm20bFifo |
| `HVM_SYSREG_HIST` | hvm::DbtX64Backend::Callbacks::SysRegRead, hvm::DbtX64Backend::Callbacks::SysRegWrite, hvm::DbtX64Backend::periodic_stats |
| `HVM_TELE_OUT` | hvm::run_app |
| `HVM_TEST_MPROT_FAIL` | hvm::ShadowMapper::protect_aliases_locked |
| `HVM_TIME_SCALE` | hvm::VirtualClock::configure |
| `HVM_TLBCHECK` | hvm::DmaPort::to_gpa, hvm::T210Smmu::reg_write |
| `HVM_TLBIPC` | hvm::VCpu::handle_exit |
| `HVM_TLB_CHURN` | hvm::Gm20bBar1::Gm20bBar1 |
| `HVM_TRACE_STREAM` | hvm::trace::emit |
| `HVM_TSTG1` | hvm::MachineHac001::build |
| `HVM_UART_STATS` | hvm::TegraUart::push_rx, hvm::TegraUart::read, hvm::TegraUart::write |
| `HVM_V4` | hvm::gfx::vk::cmdrec::lazy_wake()::{lambda()#1}::operator(), hvm::v4::on |
| `HVM_V4_RES_MASK` | hvm::WatchSet::add_sticky, hvm::WatchSet::kind_on |
| `HVM_VBLANK_STRETCH_ALL` | hvm::T210Dc::vblank |
| `HVM_VIZ_PORT` | hvm::run_app |
| `HVM_VK_DEVICE` | hvm::eden::Bridge::create |
| `HVM_VK_NOPCACHE` | hvm::gfx::vk::Device::init_pipeline_cache |
| `HVM_VK_REC_THREADS` | hvm::gfx::vk::cmdrec::start |
| `HVM_WINDBG` | hvm::DbtX64Backend::Callbacks::MemoryWrite16, hvm::DbtX64Backend::Callbacks::MemoryWrite32, hvm::DbtX64Backend::Callbacks::MemoryWrite64, hvm::DbtX64Backend::Callbacks::MemoryWrite8, hvm::DbtX64Backen |
| `HVM_WIN_NOMOVE` | hvm::ShadowMapper::maybe_move_window |
| `HVM_WIN_REFUSE` | hvm::ShadowMapper::on_host_fault_impl |
| `HVM_WLANTRACE` | hvm::Bcm4356::doorbell, hvm::Bcm4356::handle_ctrl, hvm::Bcm4356::ioctl_exec, hvm::Bcm4356::mem_read, hvm::Bcm4356::mem_write |
| `HVM_WLAN_NOJOIN` | hvm::Bcm4356::do_join |
| `HVM_XUSB_TRACE` | hvm::MachineHac001::build |
| `HVM_YIELD_PAUSE_MAX_US` | hvm::DbtX64Backend::yield_throttle |
| `HVM_YIELD_PAUSE_US` | hvm::DbtX64Backend::yield_throttle |
