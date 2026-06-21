package tencent.im.oidb.cmd0x8fc;

import com.tencent.mobileqq.pb.ByteStringMicro;
import com.tencent.mobileqq.pb.MessageMicro;
import com.tencent.mobileqq.pb.PBBytesField;
import com.tencent.mobileqq.pb.PBEnumField;
import com.tencent.mobileqq.pb.PBField;
import com.tencent.mobileqq.pb.PBRepeatMessageField;
import com.tencent.mobileqq.pb.PBStringField;
import com.tencent.mobileqq.pb.PBUInt32Field;
import com.tencent.mobileqq.pb.PBUInt64Field;

public final class Oidb_0x8fc {

    public static final class CardNameElem extends MessageMicro<CardNameElem> {
        public static final int CARD_TYPE_TEXT = 1;
        public static final int CARD_TYPE_XC = 2;

        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(
                new int[]{8, 18},
                new String[]{"enum_card_type", "bytes_value"},
                new Object[]{1, ByteStringMicro.EMPTY},
                CardNameElem.class
        );

        public final PBEnumField enum_card_type = PBField.initEnum(1);
        public final PBBytesField bytes_value = PBField.initBytes(ByteStringMicro.EMPTY);
    }

    public static final class ClientInfo extends MessageMicro<ClientInfo> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(
                new int[]{8, 18},
                new String[]{"uint32_implat", "string_clientver"},
                new Object[]{0, ""},
                ClientInfo.class
        );

        public final PBUInt32Field uint32_implat = PBField.initUInt32(0);
        public final PBStringField string_clientver = PBField.initString("");
    }

    public static final class CommCardNameBuf extends MessageMicro<CommCardNameBuf> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(
                new int[]{10, 16},
                new String[]{"rpt_rich_card_name", "uint32_cool_id"},
                new Object[]{null, 0},
                CommCardNameBuf.class
        );

        public final PBRepeatMessageField<RichCardNameElem> rpt_rich_card_name =
                PBField.initRepeatMessage(RichCardNameElem.class);
        public final PBUInt32Field uint32_cool_id = PBField.initUInt32(0);
    }

    public static final class LevelName extends MessageMicro<LevelName> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(
                new int[]{8, 18},
                new String[]{"uint32_level", "str_name"},
                new Object[]{0, ""},
                LevelName.class
        );

        public final PBUInt32Field uint32_level = PBField.initUInt32(0);
        public final PBStringField str_name = PBField.initString("");
    }

    public static final class RichCardNameElem extends MessageMicro<RichCardNameElem> {
        static final MessageMicro.FieldMap __fieldMap__;

        static {
            ByteStringMicro empty = ByteStringMicro.EMPTY;
            __fieldMap__ = MessageMicro.initFieldMap(
                    new int[]{10, 18},
                    new String[]{"bytes_ctrl", "bytes_text"},
                    new Object[]{empty, empty},
                    RichCardNameElem.class
            );
        }

        public final PBBytesField bytes_ctrl;
        public final PBBytesField bytes_text;

        public RichCardNameElem() {
            ByteStringMicro empty = ByteStringMicro.EMPTY;
            this.bytes_ctrl = PBField.initBytes(empty);
            this.bytes_text = PBField.initBytes(empty);
        }
    }

    public static final class MemberInfo extends MessageMicro<MemberInfo> {
        static final MessageMicro.FieldMap __fieldMap__;

        static {
            ByteStringMicro empty = ByteStringMicro.EMPTY;
            __fieldMap__ = MessageMicro.initFieldMap(
                    new int[]{8, 16, 24, 32, 42, 48, 58, 66, 74, 82, 90, 96, 106, 112, 120, 130, 138, 144, 154, 160, 168},
                    new String[]{
                            "uint64_uin", "uint32_point", "uint32_active_day", "uint32_level",
                            "bytes_special_title", "uint32_special_title_expire_time",
                            "bytes_uin_name", "member_card_name", "bytes_phone", "bytes_email",
                            "bytes_remark", "uint32_gender", "bytes_job", "uint32_tribe_level",
                            "uint32_tribe_point", "rpt_rich_card_name", "bytes_comm_rich_card_name",
                            "uint32_ringtone_id", "bytes_group_honor",
                            "uint32_cmduin_flag_ex3_grocery", "uint32_cmduin_flag_ex3_mask"
                    },
                    new Object[]{
                            0L, 0, 0, 0, empty, 0, empty, empty, empty, empty, empty, 0, empty,
                            0, 0, null, empty, 0, empty, 0, 0
                    },
                    MemberInfo.class
            );
        }

        public final PBUInt64Field uint64_uin = PBField.initUInt64(0);
        public final PBUInt32Field uint32_point = PBField.initUInt32(0);
        public final PBUInt32Field uint32_active_day = PBField.initUInt32(0);
        public final PBUInt32Field uint32_level = PBField.initUInt32(0);
        public final PBBytesField bytes_special_title;
        public final PBUInt32Field uint32_special_title_expire_time;
        public final PBBytesField bytes_uin_name;
        public final PBBytesField member_card_name;
        public final PBBytesField bytes_phone;
        public final PBBytesField bytes_email;
        public final PBBytesField bytes_remark;
        public final PBUInt32Field uint32_gender;
        public final PBBytesField bytes_job;
        public final PBUInt32Field uint32_tribe_level;
        public final PBUInt32Field uint32_tribe_point;
        public final PBRepeatMessageField<CardNameElem> rpt_rich_card_name;
        public final PBBytesField bytes_comm_rich_card_name;
        public final PBUInt32Field uint32_ringtone_id;
        public final PBBytesField bytes_group_honor;
        public final PBUInt32Field uint32_cmduin_flag_ex3_grocery;
        public final PBUInt32Field uint32_cmduin_flag_ex3_mask;

        public MemberInfo() {
            ByteStringMicro empty = ByteStringMicro.EMPTY;
            this.bytes_special_title = PBField.initBytes(empty);
            this.uint32_special_title_expire_time = PBField.initUInt32(0);
            this.bytes_uin_name = PBField.initBytes(empty);
            this.member_card_name = PBField.initBytes(empty);
            this.bytes_phone = PBField.initBytes(empty);
            this.bytes_email = PBField.initBytes(empty);
            this.bytes_remark = PBField.initBytes(empty);
            this.uint32_gender = PBField.initUInt32(0);
            this.bytes_job = PBField.initBytes(empty);
            this.uint32_tribe_level = PBField.initUInt32(0);
            this.uint32_tribe_point = PBField.initUInt32(0);
            this.rpt_rich_card_name = PBField.initRepeatMessage(CardNameElem.class);
            this.bytes_comm_rich_card_name = PBField.initBytes(empty);
            this.uint32_ringtone_id = PBField.initUInt32(0);
            this.bytes_group_honor = PBField.initBytes(empty);
            this.uint32_cmduin_flag_ex3_grocery = PBField.initUInt32(0);
            this.uint32_cmduin_flag_ex3_mask = PBField.initUInt32(0);
        }
    }

    public static final class ReqBody extends MessageMicro<ReqBody> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(
                new int[]{8, 16, 26, 34, 40, 48, 56, 66, 74, 82},
                new String[]{
                        "uint64_group_code", "uint32_show_flag", "rpt_mem_level_info",
                        "rpt_level_name", "uint32_update_time", "uint32_office_mode",
                        "uint32_group_open_appid", "msg_client_info", "bytes_auth_key",
                        "rpt_level_name_new"
                },
                new Object[]{0L, 0, null, null, 0, 0, 0, null, ByteStringMicro.EMPTY, null},
                ReqBody.class
        );

        public final PBUInt64Field uint64_group_code = PBField.initUInt64(0);
        public final PBUInt32Field uint32_show_flag = PBField.initUInt32(0);
        public final PBRepeatMessageField<MemberInfo> rpt_mem_level_info =
                PBField.initRepeatMessage(MemberInfo.class);
        public final PBRepeatMessageField<LevelName> rpt_level_name =
                PBField.initRepeatMessage(LevelName.class);
        public final PBUInt32Field uint32_update_time = PBField.initUInt32(0);
        public final PBUInt32Field uint32_office_mode = PBField.initUInt32(0);
        public final PBUInt32Field uint32_group_open_appid = PBField.initUInt32(0);
        public ClientInfo msg_client_info = new ClientInfo();
        public final PBBytesField bytes_auth_key = PBField.initBytes(ByteStringMicro.EMPTY);
        public final PBRepeatMessageField<LevelName> rpt_level_name_new =
                PBField.initRepeatMessage(LevelName.class);
    }

    public static final class RspBody extends MessageMicro<RspBody> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(
                new int[]{8, 18, 26},
                new String[]{"uint64_group_code", "strErrInfo", "bytes_cool_group_card_rsp"},
                new Object[]{0L, "", ByteStringMicro.EMPTY},
                RspBody.class
        );

        public final PBUInt64Field uint64_group_code = PBField.initUInt64(0);
        public final PBStringField strErrInfo = PBField.initString("");
        public final PBBytesField bytes_cool_group_card_rsp = PBField.initBytes(ByteStringMicro.EMPTY);
    }
}
