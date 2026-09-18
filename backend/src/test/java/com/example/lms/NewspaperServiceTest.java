package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.lms.dto.NewspaperRequest;
import com.example.lms.dto.NewspaperResponse;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.ConflictException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.*;
import com.example.lms.service.NewspaperService;
import com.example.lms.util.CurrentUser;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NewspaperServiceTest {

  @Mock NewspaperRepository newspapers;
  @Mock BorrowRecordRepository records;
  @Mock ApplicationEventPublisher events;
  @Mock CurrentUser currentUser;

  @InjectMocks NewspaperService service;

  private CurrentUser.Actor actor;

  @BeforeEach
  void setUp() {
    actor = new CurrentUser.Actor(10L, "admin", "ADMIN", "127.0.0.1", "test-agent");
    lenient().when(currentUser.get()).thenReturn(actor);
  }

  @Test
  void createPublishesAuditEvent() {
    when(newspapers.saveAndFlush(any(Newspaper.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, Newspaper.class), 30L);
              return inv.getArgument(0);
            });

    NewspaperRequest req =
        new NewspaperRequest("Daily News", "News Corp", LocalDate.of(2026, 9, 1), "Headline A");
    NewspaperResponse resp = service.create(req);

    assertEquals(30L, resp.id());
    assertEquals("Daily News", resp.title());

    ArgumentCaptor<EntityAuditEvent> captor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(captor.capture());
    EntityAuditEvent event = captor.getValue();
    assertEquals(AuditAction.CREATE, event.getAction());
    assertEquals(AuditEntityType.NEWSPAPER, event.getEntityType());
    assertEquals(30L, event.getEntityId());
    assertTrue(event.getDescription().contains("Daily News"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void updatePublishesAuditEvent() {
    Newspaper existing = makeNewspaper(30L, "Daily News");
    when(newspapers.findById(30L)).thenReturn(Optional.of(existing));
    when(newspapers.saveAndFlush(any(Newspaper.class))).thenAnswer(inv -> inv.getArgument(0));

    NewspaperRequest req =
        new NewspaperRequest(
            "Daily News Updated", "News Corp", LocalDate.of(2026, 9, 2), "New Headline");
    NewspaperResponse resp = service.update(30L, req);

    assertEquals("Daily News Updated", resp.title());

    ArgumentCaptor<EntityAuditEvent> captor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(captor.capture());
    EntityAuditEvent event = captor.getValue();
    assertEquals(AuditAction.UPDATE, event.getAction());
    assertEquals(AuditEntityType.NEWSPAPER, event.getEntityType());
    assertEquals(30L, event.getEntityId());
    assertTrue(event.getDescription().contains("Daily News Updated"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void deletePublishesAuditEvent() {
    Newspaper existing = makeNewspaper(30L, "Doomed Paper");
    when(newspapers.findById(30L)).thenReturn(Optional.of(existing));
    when(records.existsByNewspaperId(30L)).thenReturn(false);

    service.delete(30L);

    verify(newspapers).delete(existing);

    ArgumentCaptor<EntityAuditEvent> captor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(captor.capture());
    EntityAuditEvent event = captor.getValue();
    assertEquals(AuditAction.DELETE, event.getAction());
    assertEquals(AuditEntityType.NEWSPAPER, event.getEntityType());
    assertEquals(30L, event.getEntityId());
    assertTrue(event.getDescription().contains("Doomed Paper"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void deleteWithBorrowHistoryThrows() {
    Newspaper existing = makeNewspaper(30L, "Borrowed Paper");
    when(newspapers.findById(30L)).thenReturn(Optional.of(existing));
    when(records.existsByNewspaperId(30L)).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.delete(30L));
    verify(events, never()).publishEvent(any());
  }

  @Test
  void updateNotFoundDoesNotPublishAudit() {
    when(newspapers.findById(99L)).thenReturn(Optional.empty());

    NewspaperRequest req =
        new NewspaperRequest("Missing", "Publisher", LocalDate.of(2026, 1, 1), "Headline");
    assertThrows(ResourceNotFoundException.class, () -> service.update(99L, req));
    verify(events, never()).publishEvent(any());
  }

  private Newspaper makeNewspaper(Long id, String title) {
    Newspaper n = new Newspaper();
    setId(n, id);
    n.setTitle(title);
    return n;
  }

  private static void setId(Object entity, Long id) {
    try {
      Field field = entity.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set id via reflection", e);
    }
  }
}
