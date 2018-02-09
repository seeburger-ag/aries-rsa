package org.apache.aries.rsa.provider.tcp.myservice;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Version;

public class PrimitiveServiceImpl implements PrimitiveService {

    @Override
    public byte callByte(byte num) {
        return num;
    }

    @Override
    public short callShort(short num) {
        return num;
    }

    
    @Override
    public int callInt(int num) {
        return num;
    }

    @Override
    public long callLong(long num) {
        return num;
    }

    @Override
    public float callFloat(float num) {
        return num;
    }

    @Override
    public double callDouble(double num) {
        return num;
    }
    
    @Override
    public boolean callBoolean(boolean bool) {
        return bool;
    }

    @Override
    public byte[] callByteAr(byte[] byteAr) {
        return byteAr;
    }

    @Override
    public Version callVersion(Version version) {
        return version;
    }
    
    @Override
    public Version[] callVersionAr(Version[] version) {
        return version;
    }

    @Override
    public List<Version> callVersionList(List<Version> version) {
        return version;
    }

    @Override
    public Map<Version, Version> callVersionMap(Map<Version, Version> map) {
        return map;
    }
    
    @Override
    public Set<Version> callVersionSet(Set<Version> set) {
        return set;
    }
    
    
    @Override
    public DTOType callDTO(DTOType dto) {
        return dto;
    }

    @Override
    public DTOType[] callDTOAr(DTOType[] dtoAr) {
        return dtoAr;
    }
}
