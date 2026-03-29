package tencent.im.oidb;

import com.tencent.mobileqq.pb.ByteStringMicro;
import com.tencent.mobileqq.pb.MessageMicro;
import com.tencent.mobileqq.pb.PBBytesField;
import com.tencent.mobileqq.pb.PBField;
import com.tencent.mobileqq.pb.PBRepeatMessageField;
import com.tencent.mobileqq.pb.PBStringField;
import com.tencent.mobileqq.pb.PBUInt32Field;

public final class oidb_sso {

    public static final class OIDBSSOPkg extends MessageMicro<OIDBSSOPkg> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{8, 16, 24, 34, 42, 50, 90}, new String[]{"uint32_command", "uint32_service_type", "uint32_result", "bytes_bodybuffer", "str_error_msg", "str_client_version", "trpc_trans_info"}, new Object[]{0, 0, 0, ByteStringMicro.EMPTY, "", "", null}, OIDBSSOPkg.class);
        public final PBUInt32Field uint32_command = PBField.initUInt32(0);
        public final PBUInt32Field uint32_service_type = PBField.initUInt32(0);
        public final PBUInt32Field uint32_result = PBField.initUInt32(0);
        public final PBBytesField bytes_bodybuffer = PBField.initBytes(ByteStringMicro.EMPTY);
        public final PBStringField str_error_msg = PBField.initString("");
        public final PBStringField str_client_version = PBField.initString("");
        public final PBRepeatMessageField<MetaData> trpc_trans_info = PBField.initRepeatMessage(MetaData.class);
    }

    public static final class MetaData extends MessageMicro<MetaData> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18}, new String[]{"key", "value"}, new Object[]{"", ""}, MetaData.class);
        public final PBStringField key = PBField.initString("");
        public final PBStringField value = PBField.initString("");
    }
}
