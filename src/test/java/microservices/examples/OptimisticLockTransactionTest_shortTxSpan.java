package microservices.examples;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;


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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

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
class OptimisticLockTransactionTest_shortTxSpan {

	@Autowired
	CourseMyBatisDAO courseMapper;
	
	@Autowired
	CourseJPARepository courseRepository;
	
	@MockBean
	MemberGateway memberGateway;

	@MockBean
	BoardGateway boardGateway;

	@Autowired
	PlatformTransactionManager transactionManager;
	
	@Autowired
	CourseService service;

	Faker f = new Faker(new Locale("ko"));

	@BeforeEach
	void setup() {
		Mockito.reset(memberGateway);
		Mockito.reset(boardGateway);
		service.setTransactionManager(transactionManager);
	}
	
	@Test
	void ???????????????_??????() {
		Course course = aCourse();
		log.error("-_-;;00-1");
		service.createCourseOptimisticLocking_shortTransactionSpan(course);
		log.error("-_-;;00-2");
		assertNotNull(service.get(course.getId()));
	}

	@Test
	void ???????????????_??????????????????_?????????????????????_??????????????????__???????????????_??????() {
		when(memberGateway.addManager(any(), any())).thenThrow(new RuntimeException("add member rest api failed!"));
		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_shortTransactionSpan(course);
		}catch (Exception e) {
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("add member rest api failed!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("???????????? ??????");
	}
	
	@Test
	void ?????????????????????_?????????????????????_??????????????????__?????????_?????????_????????????_???????????????() {
		when(boardGateway.addBoard(any(), any())).thenThrow(new RuntimeException("add board rest api failed!"));
		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_shortTransactionSpan(course);
		} catch (Exception e) {
			verify(memberGateway).removeManager(any(), any());
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("add board rest api failed!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("???????????? ??????");
	}

	@Test
	void ?????????????????????_?????????_?????????_??????_??????????????????_??????_??????????????????__?????????_???????????????_?????????_??????() {
		when(boardGateway.addBoard(any(), any())).thenThrow(new RuntimeException("add board rest api failed!"));
		doThrow(new RuntimeException("Compensation Tx Failed")).when(memberGateway).removeManager(any(), any());
		
		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_shortTransactionSpan(course);
		} catch (Exception e) {
			verify(memberGateway).addManager(any(), any());
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("add board rest api failed!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("???????????? ??????");
	}
	
	@Test
	void ??????_????????????_?????????????????????_????????????_?????????_??????() {
		PlatformTransactionManager tx = new PlatformTransactionManager() {
			public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
				return transactionManager.getTransaction(definition);
			}
			public void commit(TransactionStatus status) throws TransactionException {
				throw new RuntimeException("Failed to commit!");
			}
			public void rollback(TransactionStatus status) throws TransactionException {
				transactionManager.rollback(status);
			}
		};
		service.setTransactionManager(tx);

		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_shortTransactionSpan(course);
		} catch (Exception e) {
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("Failed to commit!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("???????????? ??????");
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
