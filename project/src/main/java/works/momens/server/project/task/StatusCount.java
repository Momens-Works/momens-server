package works.momens.server.project.task;

/** 상태별 태스크 개수 한 줄. 진행률 집계 쿼리의 projection입니다. */
record StatusCount(String status, long count) {}
