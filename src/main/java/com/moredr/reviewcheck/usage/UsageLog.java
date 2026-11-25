package com.moredr.reviewcheck.usage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UsageLog {
    private Integer id;
    private String inviteCode;  //초대코드
    private String memo;        //누가에게 준 코드 인지
    private String clientIp;    //어디서 접속했는지
    private String repoUrl;
    private LocalDateTime createdAt;    //언제 썼는지
}
