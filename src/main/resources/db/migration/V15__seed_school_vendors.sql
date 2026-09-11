-- 학과·기관(vendors) 최초 등록 (2026-09-11)
--
-- V5 가 이 테이블을 의도적으로 비워 두었습니다 — initial 이 크롤러의 사이트별 식별자와
-- 1:1 로 맞아야 하는 계약 키라, 추측해서 넣으면 연동이 깨지기 때문입니다.
-- 이제 크롤러가 실제로 수집한 목록(2026-09-10 패키지, 82종)을 받아 확정했으므로 등록합니다.
--
-- ★ initial 은 생성 후 변경할 수 없습니다 (trg_vendors_10_immutable, IN001).
--   아래 값은 크롤러 패키지의 vendor_initial 과 대조해 확정한 것입니다.
--
-- 팀 보유 목록과 달랐던 3건 — 전부 패키지의 source_url 로 확인했습니다:
--   반도체시스템공학과  SSE -> SES   (sse.inha.ac.kr)
--   인공지능공학과      AIE -> AIEE  (doai.inha.ac.kr)
--   사회교육과          SSE 유지      (socialedu.inha.ac.kr)
--     └ 팀 목록에서 SSE 가 두 학과에 중복이었는데, 크롤러는 이미 갈라 두었습니다.
--
-- 사범대 수학과는 자연대 수학과와 구분하려고 '수학교육과' 로 표기합니다.
-- (name 은 UNIQUE 가 아니라 강제는 아니지만, 목록에 '수학과' 가 둘이면 고를 수 없습니다)
--
-- homepage_url 은 패키지의 실제 source_url 도메인에서 뽑았습니다. 추측이 아닙니다.
-- 패키지에 글이 없던 곳(아직 수집 대상이 아니거나 최근 글이 없던 게시판)은 NULL 입니다.

INSERT INTO vendors (name, initial, type, homepage_url) VALUES
    ('인하대학교', 'INHA', 'SCHOOL', 'https://www.inha.ac.kr'),
    ('국제처', 'INC', 'SCHOOL', 'https://internationalcenter.inha.ac.kr'),
    ('융합연구원', 'STAR', 'SCHOOL', 'https://instar.inha.ac.kr'),
    ('프런티어창의대학', 'GEB', 'SCHOOL', 'https://generaledu.inha.ac.kr'),
    ('자유전공융합학부', 'LAS', 'SCHOOL', 'https://las.inha.ac.kr'),
    ('공학융합학부', 'ECS', 'SCHOOL', 'https://ecs.inha.ac.kr'),
    ('자연과학융합학부', 'NCS', 'SCHOOL', 'https://ncs.inha.ac.kr'),
    ('경영융합학부', 'CVB', 'SCHOOL', 'https://cvgba.inha.ac.kr'),
    ('사회과학융합학부', 'CVS', 'SCHOOL', 'https://cvgsosci.inha.ac.kr'),
    ('인문융합학부', 'CVH', 'SCHOOL', 'https://cvghuman.inha.ac.kr'),
    ('공과대학', 'IE', 'SCHOOL', 'https://engcollege.inha.ac.kr'),
    ('기계공학과', 'MEG', 'SCHOOL', 'https://mech.inha.ac.kr'),
    ('항공우주공학과', 'ASE', 'SCHOOL', 'https://aerospace.inha.ac.kr'),
    ('조선해양공학과', 'NOE', 'SCHOOL', 'https://naoe.inha.ac.kr'),
    ('산업경영공학과', 'IEN', 'SCHOOL', 'https://ie.inha.ac.kr'),
    ('화학공학과', 'CHE', 'SCHOOL', 'https://chemeng.inha.ac.kr'),
    ('고분자공학과', 'PSE', 'SCHOOL', 'https://inhapoly.inha.ac.kr'),
    ('신소재공학과', 'MSE', 'SCHOOL', 'https://dmse.inha.ac.kr'),
    ('사회인프라공학과', 'CIV', 'SCHOOL', 'https://civil.inha.ac.kr'),
    ('환경공학과', 'ENV', 'SCHOOL', 'https://environment.inha.ac.kr'),
    ('공간정보공학과', 'GEO', 'SCHOOL', 'https://geoinfo.inha.ac.kr'),
    ('건축학부', 'ARC', 'SCHOOL', 'https://arch.inha.ac.kr'),
    ('에너지자원공학과', 'ENR', 'SCHOOL', 'https://eneres.inha.ac.kr'),
    ('전기전자공학부', 'EEC', 'SCHOOL', 'https://ee.inha.ac.kr'),
    ('반도체시스템공학과', 'SES', 'SCHOOL', 'https://sse.inha.ac.kr'),
    ('반도체공학(융합전공)', 'SEF', 'SCHOOL', NULL),
    ('이차전지융합학과', 'IB', 'SCHOOL', 'https://ibattery.inha.ac.kr'),
    ('이차전지공학(융합전공)', 'IBF', 'SCHOOL', NULL),
    ('공학교육혁신센터', 'CEE', 'SCHOOL', 'https://icee.inha.ac.kr'),
    ('소프트웨어융합대학', 'ITCU', 'SCHOOL', 'https://swcc.inha.ac.kr'),
    ('인공지능공학과', 'AIEE', 'SCHOOL', 'https://doai.inha.ac.kr'),
    ('데이터사이언스학과', 'DSC', 'SCHOOL', 'https://datascience.inha.ac.kr'),
    ('스마트모빌리티공학', 'SME', 'SCHOOL', 'https://sme.inha.ac.kr'),
    ('디자인테크놀로지학과', 'DET', 'SCHOOL', 'https://designtech.inha.ac.kr'),
    ('컴퓨터공학과', 'CSE', 'SCHOOL', 'https://cse.inha.ac.kr'),
    ('소프트웨어중심대학사업단', 'SWU', 'SCHOOL', 'https://swuniv.inha.ac.kr'),
    ('인공지능융합연구센터', 'AIX', 'SCHOOL', 'https://aix.inha.ac.kr'),
    ('자연과학대학', 'INS', 'SCHOOL', 'https://nscollege.inha.ac.kr'),
    ('수학과', 'MTH', 'SCHOOL', 'https://math.inha.ac.kr'),
    ('통계학과', 'STS', 'SCHOOL', 'https://statistics.inha.ac.kr'),
    ('물리학과', 'PHY', 'SCHOOL', 'https://physics.inha.ac.kr'),
    ('화학과', 'CHM', 'SCHOOL', 'https://chemistry.inha.ac.kr'),
    ('해양과학과', 'OCN', 'SCHOOL', 'https://ocean.inha.ac.kr'),
    ('식품영양학과', 'IFN', 'SCHOOL', 'https://foodnutri.inha.ac.kr'),
    ('바이오시스템융합학부', 'BIO', 'SCHOOL', 'https://biosyst.inha.ac.kr'),
    ('생명공학과', 'IBE', 'SCHOOL', 'https://bio.inha.ac.kr'),
    ('바이오제약공학과', 'BPH', 'SCHOOL', NULL),
    ('생명과학과', 'IBG', 'SCHOOL', 'https://biology.inha.ac.kr'),
    ('첨단바이오의약학과', 'BMD', 'SCHOOL', 'https://biomedical.inha.ac.kr'),
    ('바이오식품공학과', 'FST', 'SCHOOL', 'https://foodscience.inha.ac.kr'),
    ('경영대학', 'CBA', 'SCHOOL', 'https://cba.inha.ac.kr'),
    ('경영학과', 'BUS', 'SCHOOL', 'https://biz.inha.ac.kr'),
    ('파이낸스경영학과', 'GFB', 'SCHOOL', NULL),
    ('아태물류학부', 'APL', 'SCHOOL', 'https://apsl.inha.ac.kr'),
    ('국제통상학과', 'INT', 'SCHOOL', 'https://star.inha.ac.kr'),
    ('기후위기대응(융합전공)', 'HUSS', 'SCHOOL', 'https://inhahuss.inha.ac.kr'),
    ('정석물류통상연구원', 'JRI', 'SCHOOL', NULL),
    ('사범대학', 'EDC', 'SCHOOL', 'https://edcollege.inha.ac.kr'),
    ('국어교육과', 'EKR', 'SCHOOL', 'https://koreanedu.inha.ac.kr'),
    ('영어교육과', 'EEG', 'SCHOOL', 'https://dele.inha.ac.kr'),
    ('사회교육과', 'SSE', 'SCHOOL', 'https://socialedu.inha.ac.kr'),
    ('교육학과', 'EDU', 'SCHOOL', 'https://education.inha.ac.kr'),
    ('체육교육과', 'PHE', 'SCHOOL', 'https://physicaledu.inha.ac.kr'),
    ('수학교육과', 'EMT', 'SCHOOL', 'https://mathed.inha.ac.kr'),
    ('사회과학대학', 'SSC', 'SCHOOL', 'https://sscollege.inha.ac.kr'),
    ('행정학과', 'PAD', 'SCHOOL', 'https://publicad.inha.ac.kr'),
    ('정치외교학과', 'POL', 'SCHOOL', 'https://political.inha.ac.kr'),
    ('미디어커뮤니케이션학과', 'COM', 'SCHOOL', 'https://comm.inha.ac.kr'),
    ('경제학과', 'ECO', 'SCHOOL', 'https://econ.inha.ac.kr'),
    ('소비자학과', 'CON', 'SCHOOL', 'https://consumer.inha.ac.kr'),
    ('아동심리학과', 'CHS', 'SCHOOL', 'https://child.inha.ac.kr'),
    ('사회복지학과', 'SWE', 'SCHOOL', 'https://welfare.inha.ac.kr'),
    ('문과대학', 'HAC', 'SCHOOL', NULL),
    ('한국어문학과', 'HKO', 'SCHOOL', 'https://korean.inha.ac.kr'),
    ('사학과', 'HIS', 'SCHOOL', 'https://history.inha.ac.kr'),
    ('철학과', 'PHI', 'SCHOOL', 'https://philosophy.inha.ac.kr'),
    ('중국학과', 'CHN', 'SCHOOL', 'https://chinese.inha.ac.kr'),
    ('일본언어문화학과', 'JPN', 'SCHOOL', 'https://japan.inha.ac.kr'),
    ('영미유럽인문융합학부', 'EES', 'SCHOOL', 'https://ees.inha.ac.kr'),
    ('문화콘텐츠문화경영학과', 'CCM', 'SCHOOL', 'https://culturecm.inha.ac.kr'),
    ('의과대학', 'UMD', 'SCHOOL', 'https://medicine.inha.ac.kr'),
    ('간호대학', 'NUR', 'SCHOOL', 'https://nursing.inha.ac.kr'),
    ('예술체육대학', 'ASC', 'SCHOOL', 'https://artsports.inha.ac.kr'),
    ('조형예술학과', 'FAT', 'SCHOOL', 'https://finearts.inha.ac.kr'),
    ('디자인융합학과', 'CDN', 'SCHOOL', 'https://design.inha.ac.kr'),
    ('스포츠과학과', 'KIN', 'SCHOOL', 'https://sport.inha.ac.kr'),
    ('연극영화학과', 'IPS', 'SCHOOL', 'https://theatrefilm.inha.ac.kr'),
    ('의류디자인학과', 'FDT', 'SCHOOL', 'https://fashion.inha.ac.kr')
ON CONFLICT (initial) DO NOTHING;


-- 확인 — 개수가 어긋난 채 배포되면 적재가 통째로 실패합니다
DO $$
DECLARE n int;
BEGIN
    SELECT count(*) INTO n FROM vendors WHERE type = 'SCHOOL' AND is_active;
    IF n < 88 THEN
        RAISE EXCEPTION '활성 SCHOOL 제공처가 %개뿐입니다 (기대 88 이상)', n;
    END IF;
END $$;
