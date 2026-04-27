package com.tencent.qqnt.kernel.nativeinterface;

public interface IKernelLoginService {

    final class CppProxy implements IKernelLoginService {

        public static native IKernelLoginService getLoginService();

        @Override
        public void cancelRapidLogin(String str, IRapidLoginCallback iRapidLoginCallback) {
        }

        @Override
        public void changeLimit(ChangeLimitReq changeLimitReq, IChangeLimitCallback iChangeLimitCallback) {
        }

        @Override
        public void checkGatewayCode(CheckGatewayCodeReq checkGatewayCodeReq, ILoginCallback iLoginCallback) {
        }

        @Override
        public void checkLimitHandleResult(CheckLimitHandleResultReq checkLimitHandleResultReq, IRegisterIdentityCallback iRegisterIdentityCallback) {
        }

        @Override
        public void checkSms(CheckSmsReqBody checkSmsReqBody, ILoginCallback iLoginCallback) {
        }

        @Override
        public void checkThirdCode(CheckThirdCodeReq checkThirdCodeReq, ICheckThirdCodeCallback iCheckThirdCodeCallback) {
        }

        @Override
        public void checkUpSms(CheckSmsReqBody checkSmsReqBody, ILoginCallback iLoginCallback) {
        }

        @Override
        public void deleteAllLoginTicket() {
        }

        @Override
        public void deleteLoginA2Ticket(long j, long j2) {
        }

        @Override
        public void deleteLoginTicket(long j, long j2) {
        }

        @Override
        public void easyLogin(long j, AppInfo appInfo, ILoginCallback iLoginCallback) {
        }

        @Override
        public void getAllLoginTicket(ILoginTicketListCallback iLoginTicketListCallback) {
        }

        @Override
        public void getLoginTicketByUin(long j, long j2, ILoginTicketCallback iLoginTicketCallback) {
        }

        @Override
        public void getRegisterSmsCode(GetRegisterSmsCodeReq getRegisterSmsCodeReq, IRegisterIdentityCallback iRegisterIdentityCallback) {
        }

        @Override
        public void getRegisterUin(GetRegisterUinReq getRegisterUinReq, IGetRegisterUinCallback iGetRegisterUinCallback) {
        }

        @Override
        public void getSms(GetSmsReqBody getSmsReqBody, ILoginCallback iLoginCallback) {
        }

        @Override
        public void getUpSmsInfo(GetSmsReqBody getSmsReqBody, IGetUpSmsCallback iGetUpSmsCallback) {
        }

        @Override
        public void initConfig(InitLoginConfig initLoginConfig, IloginAdapter iloginAdapter) {
        }

        @Override
        public void optimusLogin(OptimusLoginInfo optimusLoginInfo, ILoginCallback iLoginCallback) {
        }

        @Override
        public void passwordLogin(PwdLoginInfo pwdLoginInfo, ILoginCallback iLoginCallback) {
        }

        @Override
        public void rapidLogin(RapidLoginReq rapidLoginReq, IRapidLoginCallback iRapidLoginCallback) {
        }

        @Override
        public void refreshLoginTicketsByUin(long j, AppInfo appInfo, boolean z, ILoginRefreshTicketCallback iLoginRefreshTicketCallback) {
        }

        @Override
        public void sendAuthQrRequest(AuthQrReqInfo authQrReqInfo, ICommonCallback iCommonCallback) {
        }

        @Override
        public void sendCancleQrRequest(String str, byte[] bArr, ICommonCallback iCommonCallback) {
        }

        @Override
        public void sendRejectQrRequest(String str, byte[] bArr, ICommonCallback iCommonCallback) {
        }

        @Override
        public void sendScanQrRequest(ScanQrReq scanQrReq, IScanQRCodeCallback iScanQRCodeCallback) {
        }

        @Override
        public void setCurrentUin(String str) {
        }

        @Override
        public void setGuid(String str) {
        }

        @Override
        public void setQimei(String str) {
        }

        @Override
        public void submitIdentityInfo(SubmitIdentityInfoReq submitIdentityInfoReq, IRegisterIdentityCallback iRegisterIdentityCallback) {
        }

        @Override
        public void verifyNewDeviceWithPwd(NewDeviceVerifyReq newDeviceVerifyReq, IVerifyNewDeviceCallback iVerifyNewDeviceCallback) {
        }
    }

    void cancelRapidLogin(String str, IRapidLoginCallback iRapidLoginCallback);

    void changeLimit(ChangeLimitReq changeLimitReq, IChangeLimitCallback iChangeLimitCallback);

    void checkGatewayCode(CheckGatewayCodeReq checkGatewayCodeReq, ILoginCallback iLoginCallback);

    void checkLimitHandleResult(CheckLimitHandleResultReq checkLimitHandleResultReq, IRegisterIdentityCallback iRegisterIdentityCallback);

    void checkSms(CheckSmsReqBody checkSmsReqBody, ILoginCallback iLoginCallback);

    void checkThirdCode(CheckThirdCodeReq checkThirdCodeReq, ICheckThirdCodeCallback iCheckThirdCodeCallback);

    void checkUpSms(CheckSmsReqBody checkSmsReqBody, ILoginCallback iLoginCallback);

    void deleteAllLoginTicket();

    void deleteLoginA2Ticket(long j, long j2);

    void deleteLoginTicket(long j, long j2);

    void easyLogin(long j, AppInfo appInfo, ILoginCallback iLoginCallback);

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
