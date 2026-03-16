package NS_COMM;

import com.tencent.mobileqq.pb.ByteStringMicro;
import com.tencent.mobileqq.pb.MessageMicro;
import com.tencent.mobileqq.pb.PBBytesField;
import com.tencent.mobileqq.pb.PBField;
import com.tencent.mobileqq.pb.PBInt32Field;
import com.tencent.mobileqq.pb.PBRepeatMessageField;
import com.tencent.mobileqq.pb.PBStringField;
import com.tencent.mobileqq.pb.PBUInt32Field;

public final class COMM {

    public static final class Entry extends MessageMicro<Entry> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18}, new String[]{"key", "value"}, new Object[]{"", ""}, Entry.class);
        public final PBStringField key = PBField.initString("");
        public final PBStringField value = PBField.initString("");
    }

    public static final class StCommonExt extends MessageMicro<StCommonExt> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18}, new String[]{"mapInfo", "attachInfo"}, new Object[]{null, ""}, StCommonExt.class);
        public final PBRepeatMessageField<Entry> mapInfo = PBField.initRepeatMessage(COMM.Entry.class);
        public final PBStringField attachInfo = PBField.initString("");
    }

    public static final class Result extends MessageMicro<Result> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{8, 18}, new String[]{"retCode", "msg"}, new Object[]{0, ""}, Result.class);
        public final PBInt32Field retCode = PBField.initInt32(0);
        public final PBStringField msg = PBField.initString("");
    }

    public static final class User extends MessageMicro<User> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{8, 18, 26}, new String[]{"uin", "appid", "openid"}, new Object[]{0, "", ""}, User.class);
        public final PBUInt32Field uin = PBField.initUInt32(0);
        public final PBStringField appid = PBField.initString("");
        public final PBStringField openid = PBField.initString("");
    }

    public static final class BytesEntry extends MessageMicro<BytesEntry> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18}, new String[]{"key", "value"}, new Object[]{"", ByteStringMicro.EMPTY}, BytesEntry.class);
        public final PBStringField key = PBField.initString("");
        public final PBBytesField value = PBField.initBytes(ByteStringMicro.EMPTY);
    }

    public static final int bitmap_svr = 13140;
    public static final int certified_account_read = 10300;
    public static final int certified_account_write = 10301;
    public static final int comm_limit = 12004;
    public static final int follow_svr = 13141;
    public static final int hexlog = 11111;
    public static final int ipinfo = 16889;
    public static final int kafka_produce_proxy = 10006;
    public static final int kafka_produce_proxy_udp = 10016;
    public static final int mail_proxy = 12005;
    public static final int message_proxy = 10008;
    public static final int mini_admin_access = 10120;
    public static final int mini_app_ad = 10010;
    public static final int mini_app_admin = 10118;
    public static final int mini_app_api = 10123;
    public static final int mini_app_audit_admin = 10117;
    public static final int mini_app_cloudstorage = 10005;
    public static final int mini_app_dcreport = 10001;
    public static final int mini_app_file = 10119;
    public static final int mini_app_follow = 10003;
    public static final int mini_app_geo = 12001;
    public static final int mini_app_info = 10130;
    public static final int mini_app_ip_analyzer = 12002;
    public static final int mini_app_list = 10002;
    public static final int mini_app_mdebug = 10139;
    public static final int mini_app_music = 10009;
    public static final int mini_app_operation_admin = 10116;
    public static final int mini_app_pay = 10137;
    public static final int mini_app_qzone_recommend = 12006;
    public static final int mini_app_search = 10131;
    public static final int mini_app_share = 10007;
    public static final int mini_app_switch_indicator = 12003;
    public static final int mini_app_upload = 10170;
    public static final int mini_app_userapp = 10004;
    public static final int mini_base_admin = 10141;
    public static final int mini_base_appinfo = 10160;
    public static final int mini_base_auth = 10135;
    public static final int mini_base_developer = 10161;
    public static final int mini_base_mcode = 10132;
    public static final int mini_base_userinfo = 10133;
    public static final int mini_program_auth = 10122;
    public static final int mini_program_http = 10121;
    public static final int mini_user_info = 10151;
    public static final int mini_webapp = 18011;
    public static final int mini_webapp_jce = 10101;
    public static final int oidb_proxy_svr = 18001;
    public static final int qq_story_adapt = 10205;
    public static final int qq_story_client = 10200;
    public static final int qq_story_config = 10204;
    public static final int qq_story_notify = 10206;
    public static final int qq_story_operation = 10203;
    public static final int qq_story_strdeal = 10208;
    public static final int qq_story_transcode = 10201;
    public static final int qq_story_unread = 10202;
    public static final int qzone_web_protecter = 16891;
    public static final int security_bypass = 13143;
    public static final int security_report = 13142;
    public static final int snslog = 18888;
    public static final int store_app_admin = 10011;
    public static final int store_app_client = 10012;
    public static final int tokenauth = 16890;

    private COMM() {
    }
}
