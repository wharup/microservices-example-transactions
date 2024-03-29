package microservices.examples;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.github.javafaker.Book;
import com.github.javafaker.Faker;

import lombok.extern.slf4j.Slf4j;
import microservices.examples.tx.Application;
import microservices.examples.tx.course.Course;
import microservices.examples.tx.course.CourseJPARepository;
import microservices.examples.tx.course.CourseMyBatisDAO;
import microservices.examples.tx.course.CourseService;
import microservices.examples.tx.gateway.BoardGateway;
import microservices.examples.tx.gateway.MemberGateway;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Slf4j
class PessimisticLockTransactionTest {

	@Autowired
	CourseMyBatisDAO courseMapper;
	
	@Autowired
	CourseJPARepository courseRepository;
	
	@MockBean
	MemberGateway memberGateway;

	@MockBean
	BoardGateway boardGateway;

	@Autowired
	CourseService service;

	Faker f = new Faker(new Locale("ko"));

	@BeforeEach
	void setup() {
		Mockito.reset(memberGateway);
		Mockito.reset(boardGateway);
	}
	
	@Test
	void 정상적으로_생성() {
		Course course = aCourse();
		service.createCourse(course);
		Course actual = service.get(course.getId());
		assertNotNull(actual);
	}

	@Test
	void 코스생성후_멤버서비스에_관리자생성하다_에러발생하면__생성한코스_롤백() {
		when(memberGateway.addManager(any(), any())).thenThrow(new RuntimeException("add member rest api failed!"));
		Course course = aCourse();
		try {
			service.createCourse(course);
		}catch (Exception e) {
			assertEquals("add member rest api failed!", e.getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}
	
	@Test
	void 게시판서비스에_게시판생성하다_에러발생하면__생성한_관리자_삭제하고_코스도롤백() {
		when(boardGateway.addBoard(any(), any())).thenThrow(new RuntimeException("add board rest api failed!"));
		Course course = aCourse();
		try {
			service.createCourse(course);
		} catch (Exception e) {
			verify(memberGateway).removeManager(any(), any());
			assertEquals("add board rest api failed!", e.getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}

	@Test
	void 게시판생성하다_에러로_관리자_생성_보상트랜잭션_중에_에러발생하면__적절히_로그남기고_나머지_롤백() {
		when(boardGateway.addBoard(any(), any())).thenThrow(new RuntimeException("add board rest api failed!"));
		doThrow(new RuntimeException("Compensation Tx Failed")).when(memberGateway).removeManager(any(), any());
		
		Course course = aCourse();
		try {
			service.createCourse(course);
		} catch (Exception e) {
			verify(memberGateway).addManager(any(), any());
			assertEquals("add board rest api failed!", e.getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}
	

	private Course aCourse() {

		Course c = new Course();
		Instant now = Instant.now();
		c.setCreated(now);
		c.setUpdated(now);
		c.setId(UUID.randomUUID().toString());
		Book book = f.book();
		c.setTitle(book.title());
		c.setDescription(book.genre());
		return c;
	}

}
