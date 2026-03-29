package com.tencent.mobileqq.pb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class PBRepeatField<T> extends PBField<List<T>> {
    private final PBField<T> helper;
    private List<T> value = Collections.emptyList();

    public PBRepeatField(PBField<T> pBField) {
        this.helper = pBField;
    }

    public void add(T t) {
        get().add(t);
    }

    public void addAll(Collection<T> collection) {
        get().addAll(collection);
    }

    public void clear(Object obj) {
        this.value = Collections.emptyList();
    }

    public int computeSize(int i) {
        return computeSizeDirectly(i, (List) this.value);
    }

    protected void copyFrom(PBField<List<T>> pBField) {
        PBRepeatField pBRepeatField = (PBRepeatField) pBField;
        if (pBRepeatField.isEmpty()) {
            this.value = Collections.emptyList();
            return;
        }
        List<T> list = get();
        list.clear();
        list.addAll(pBRepeatField.value);
    }

    public T get(int i) {
        return this.value.get(i);
    }

    public boolean has() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this.value.isEmpty();
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        add(this.helper.readFromDirectly(codedInputStreamMicro));
    }

    public void remove(int i) {
        get().remove(i);
    }

    public void set(int i, T t) {
        this.value.set(i, t);
    }

    public int size() {
        return this.value.size();
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        writeToDirectly(codedOutputStreamMicro, i, (List) this.value);
    }

    public int computeSizeDirectly(int i, List<T> list) {
        Iterator<T> it = list.iterator();
        int i2 = 0;
        while (it.hasNext()) {
            i2 += this.helper.computeSizeDirectly(i, it.next());
        }
        return i2;
    }

    public List<T> get() {
        if (this.value == Collections.emptyList()) {
            this.value = new ArrayList();
        }
        return this.value;
    }

    public List<T> readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        throw new RuntimeException("PBRepeatField not support readFromDirectly method.");
    }

    public void set(List<T> list) {
        this.value = list;
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, List<T> list) throws IOException {
        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            this.helper.writeToDirectly(codedOutputStreamMicro, i, it.next());
        }
    }
}
