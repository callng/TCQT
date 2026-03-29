package com.tencent.qqnt.kernel.nativeinterface;

public interface IKernelLoginService {
    void cancelRapidLogin(String str, IRapidLoginCallback iRapidLoginCallback);

    void changeLimit(ChangeLimitReq changeLimitReq, IChangeLimitCallback iChangeLimitCallback);

    void checkGatewayCode(CheckGatewayCodeReq checkGatewayCodeReq, ILoginCallback iLoginCallback);

    void checkLimitHandleResult(CheckLimitHandleResultReq checkLimitHandleResultReq, IRegisterIdentityCallback iRegisterIdentityCallback);

    void checkSms(CheckSmsReqBody checkSmsReqBody, ILoginCallback iLoginCallback);

    void checkThirdCode(CheckThirdCodeReq checkThirdCodeReq, ICheckThirdCodeCallback iCheckThirdCodeCallback);

    void checkUpSms(CheckSmsReqBody checkSmsReqBody, ILoginCallback iLoginCallback);

    void deleteAllLoginTicket();

    void deleteLoginTicket(long j, long j2);

    void getAllLoginTicket(ILoginTicketListCallback iLoginTicketListCallback);

    void getLoginTicketByUin(long j, long j2, ILoginTicketCallback iLoginTicketCallback);

    void getRegisterSmsCode(GetRegisterSmsCodeReq getRegisterSmsCodeReq, IRegisterIdentityCallback iRegisterIdentityCallback);

    void getRegisterUin(GetRegisterUinReq getRegisterUinReq, IGetRegisterUinCallback iGetRegisterUinCallback);

    void getSms(GetSmsReqBody getSmsReqBody, ILoginCallback iLoginCallback);

    void getUpSmsInfo(GetSmsReqBody getSmsReqBody, IGetUpSmsCallback iGetUpSmsCallback);

    void initConfig(InitLoginConfig initLoginConfig, IloginAdapter iloginAdapter);

    void optimusLogin(OptimusLoginInfo optimusLoginInfo, ILoginCallback iLoginCallback);

    void passwordLogin(PwdLoginInfo pwdLoginInfo, ILoginCallback iLoginCallback);

    void rapidLogin(RapidLoginReq rapidLoginReq, IRapidLoginCallback iRapidLoginCallback);

    void refreshLoginTicketsByUin(long j, AppInfo appInfo, boolean z, ILoginRefreshTicketCallback iLoginRefreshTicketCallback);

    void sendAuthQrRequest(AuthQrReqInfo authQrReqInfo, ICommonCallback iCommonCallback);

    void sendCancleQrRequest(String str, byte[] bArr, ICommonCallback iCommonCallback);

    void sendRejectQrRequest(String str, byte[] bArr, ICommonCallback iCommonCallback);

    void sendScanQrRequest(ScanQrReq scanQrReq, IScanQRCodeCallback iScanQRCodeCallback);

    void setCurrentUin(String str);

    void setGuid(String str);

    void setQimei(String str);

    void submitIdentityInfo(SubmitIdentityInfoReq submitIdentityInfoReq, IRegisterIdentityCallback iRegisterIdentityCallback);

    void verifyNewDeviceWithPwd(NewDeviceVerifyReq newDeviceVerifyReq, IVerifyNewDeviceCallback iVerifyNewDeviceCallback);
}
