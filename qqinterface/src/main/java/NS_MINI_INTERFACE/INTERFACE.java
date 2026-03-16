package NS_MINI_INTERFACE;

import com.tencent.mobileqq.pb.MessageMicro;
import com.tencent.mobileqq.pb.PBDoubleField;
import com.tencent.mobileqq.pb.PBField;
import com.tencent.mobileqq.pb.PBInt32Field;
import com.tencent.mobileqq.pb.PBInt64Field;
import com.tencent.mobileqq.pb.PBRepeatMessageField;
import com.tencent.mobileqq.pb.PBStringField;
import NS_COMM.COMM;

public final class INTERFACE {

    public static final class StJudgeTimingReq extends MessageMicro<StJudgeTimingReq> {

        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18, 24, 32, 40, 48, 56, 64, 72, 82, 90, 96, 106, 114}, new String[]{"extInfo", "appid", "factType", "duration", "reportTime", "afterCertify", "appType", "scene", "totalTime", "launchId", "via", "AdsTotalTime", "hostExtInfo", "sourceID"}, new Object[]{null, "", 0, 0, 0L, 0, 0, 0, 0, "", "", 0, "", ""}, StJudgeTimingReq.class);
        public COMM.StCommonExt extInfo = new COMM.StCommonExt();
        public final PBStringField appid = PBField.initString("");
        public final PBInt32Field factType = PBField.initInt32(0);
        public final PBInt32Field duration = PBField.initInt32(0);
        public final PBInt64Field reportTime = PBField.initInt64(0);
        public final PBInt32Field afterCertify = PBField.initInt32(0);
        public final PBInt32Field appType = PBField.initInt32(0);
        public final PBInt32Field scene = PBField.initInt32(0);
        public final PBInt32Field totalTime = PBField.initInt32(0);
        public final PBStringField launchId = PBField.initString("");
        public final PBStringField via = PBField.initString("");
        public final PBInt32Field AdsTotalTime = PBField.initInt32(0);
        public final PBStringField hostExtInfo = PBField.initString("");
        public final PBStringField sourceID = PBField.initString("");
    }

    public final static class StJudgeTimingRsp extends MessageMicro<StJudgeTimingRsp> {

        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18, 24, 34, 42, 50}, new String[]{"extInfo", "timingTraceId", "nextDuration", "loginInstructions", "loginTraceId", "timingInstructions"}, new Object[]{null, "", 0, null, "", null}, StJudgeTimingRsp.class);
        public final PBStringField timingTraceId = PBField.initString("");
        public final PBInt32Field nextDuration = PBField.initInt32(0);
        public final PBRepeatMessageField<GuardInstruction> loginInstructions = PBField.initRepeatMessage(INTERFACE.GuardInstruction.class);
        public final PBStringField loginTraceId = PBField.initString("");
        public final PBRepeatMessageField<GuardInstruction> timingInstructions = PBField.initRepeatMessage(INTERFACE.GuardInstruction.class);
    }

    public final static class GuardInstruction extends MessageMicro<GuardInstruction> {

        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{8, 18, 26, 34, 40, 50, 57, 66}, new String[]{"type", "title", "msg", "url", "modal", "data", "ratio", "ruleName"}, new Object[]{0, "", "", "", 0, "", Double.valueOf(0.0d), ""}, GuardInstruction.class);
        public final PBInt32Field type = PBField.initInt32(0);
        public final PBStringField title = PBField.initString("");
        public final PBStringField msg = PBField.initString("");
        public final PBStringField url = PBField.initString("");
        public final PBInt32Field modal = PBField.initInt32(0);
        public final PBStringField data = PBField.initString("");
        public final PBDoubleField ratio = PBField.initDouble(0.0d);
        public final PBStringField ruleName = PBField.initString("");
    }
}
