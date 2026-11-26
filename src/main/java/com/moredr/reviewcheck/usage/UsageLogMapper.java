package com.moredr.reviewcheck.usage;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UsageLogMapper {

    //초대코드를 사용한 기록
    @Insert("""
        INSERT INTO invite (invite_code, memo, client_ip, repo_url)
        VALUES (#{inviteCode}, #{memo}, #{clientIp}, #{repoUrl})
        """)
    void insert(UsageLog usageLog);

    //ip를 몇번 썼는지 조회
    @Select("""
        SELECT COUNT(*) FROM invite
        WHERE client_ip = #{ip} AND created_at >= CURDATE()
        """)
    int countTodayByIp(String ip);

}
