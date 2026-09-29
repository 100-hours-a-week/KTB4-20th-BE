-- 드롭다운으로 펼쳐 보여줄 수 있도록, 운영 약관을 4~5줄의 단정형(-다) 문체로 간결하게 다시 구성한다.

UPDATE `chat_policy_versions`
SET `status` = 'RETIRED',
    `updated_at` = CURRENT_TIMESTAMP(6)
WHERE `status` = 'ACTIVE';

INSERT INTO `chat_policy_versions` (
    `version`,
    `title`,
    `content`,
    `status`,
    `effective_at`
) VALUES (
    '2026-09-28',
    '지역 오픈채팅 운영 약관',
    '지역 오픈채팅은 여행방과 무관하게 지역 정보를 나누는 공개 채널이며, 닉네임과 프로필이 함께 공개된다.\n욕설, 도배, 반복 전송 등 다른 이용자를 방해하는 행위는 금지한다.\n위반이 3회 누적될 때마다 1일 → 4일 → 7일 → 14일 → 30일 → 60일 순으로 전송을 제한한다.\n이미지는 JPEG, PNG, WebP 형식만 최대 5MB까지 전송할 수 있다.\n다른 사람의 개인정보나 권리를 침해하는 내용은 공유할 수 없다.',
    'ACTIVE',
    '2026-09-28 00:00:00.000000'
);
