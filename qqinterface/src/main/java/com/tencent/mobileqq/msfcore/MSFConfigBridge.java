package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;

public interface MSFConfigBridge {
    void setMSFCoreModifyConfig(@NonNull MSFModifyConfig mSFModifyConfig);

    void setMSFDualEngineConfig(@NonNull MSFDualEngineConfig mSFDualEngineConfig);

    void setMSFGeneralConfig(@NonNull MSFGeneralConfig mSFGeneralConfig);

    void setMSFMultiTcpConfig(@NonNull MSFMultiTcpConfig mSFMultiTcpConfig);

    void setMSFNetworkConfig(@NonNull MSFNetworkConfig mSFNetworkConfig);

    void setMSFPacketCombineConfig(@NonNull MSFPacketCombineConfig mSFPacketCombineConfig);

    void setMSFPkgCompressConfig(@NonNull MSFPkgCompressConfig mSFPkgCompressConfig);

    void setMSFPkgStatisticsConfig(@NonNull MSFPkgStatisticsConfig mSFPkgStatisticsConfig);

    void setMSFQuicEngineConfig(@NonNull MSFQuicEngineConfig mSFQuicEngineConfig);

    void setMSFSSIDConfig(@NonNull MSFSSIDConfig mSFSSIDConfig);

    void setMSFServerListInvalidConfig(@NonNull MSFServerListInvalidConfig mSFServerListInvalidConfig);

    void setMSFSideWayHttpConfig(@NonNull MSFSideWayHttpConfig mSFSideWayHttpConfig);

    void setMSFSideWayUdpConfig(@NonNull MSFSideWayUdpConfig mSFSideWayUdpConfig);

    void setMSFTcpEngineConfig(@NonNull MSFTcpEngineConfig mSFTcpEngineConfig);

    void setMSFTraceConfig(@NonNull MSFTraceConfig mSFTraceConfig);
}
