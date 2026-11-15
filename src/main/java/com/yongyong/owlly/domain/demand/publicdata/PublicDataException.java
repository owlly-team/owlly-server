package com.yongyong.owlly.domain.demand.publicdata;

/** 공공데이터 호출·응답이 실패했을 때. 수요 산출은 이를 잡아 추정치로 대체한다(파이프라인을 막지 않는다). */
public class PublicDataException extends RuntimeException {

    public PublicDataException(String message) {
        super(message);
    }

    public PublicDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
