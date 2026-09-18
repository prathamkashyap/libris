package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.lms.dto.MagazineRequest;
import com.example.lms.dto.MagazineResponse;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.ConflictException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.*;
import com.example.lms.service.MagazineService;
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
class MagazineServiceTest {

  @Mock MagazineRepository magazines;
  @Mock BorrowRecordRepository records;
  @Mock ApplicationEventPublisher events;
  @Mock CurrentUser currentUser;

  @InjectMocks MagazineService service;

  private CurrentUser.Actor actor;

  @BeforeEach
  void setUp() {
    actor = new CurrentUser.Actor(10L, "admin", "ADMIN", "127.0.0.1", "test-agent");
    lenient().when(currentUser.get()).thenReturn(actor);
  }

  @Test
  void createPublishesAuditEvent() {
    when(magazines.saveAndFlush(any(Magazine.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, Magazine.class), 20L);
              return inv.getArgument(0);
            });

    MagazineRequest req =
        new MagazineRequest("Time", "Time Inc.", LocalDate.of(2026, 1, 15), "Science", "AI");
    MagazineResponse resp = service.create(req);

    assertEquals(20L, resp.id());
    assertEquals("Time", resp.title());

    ArgumentCaptor<EntityAuditEvent> captor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(captor.capture());
    EntityAuditEvent event = captor.getValue();
    assertEquals(AuditAction.CREATE, event.getAction());
    assertEquals(AuditEntityType.MAGAZINE, event.getEntityType());
    assertEquals(20L, event.getEntityId());
    assertTrue(event.getDescription().contains("Time"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void updatePublishesAuditEvent() {
    Magazine existing = makeMagazine(20L, "Time");
    when(magazines.findById(20L)).thenReturn(Optional.of(existing));
    when(magazines.saveAndFlush(any(Magazine.class))).thenAnswer(inv -> inv.getArgument(0));

    MagazineRequest req =
        new MagazineRequest("Time Updated", "Time Inc.", LocalDate.of(2026, 6, 1), "Science", "AI");
    MagazineResponse resp = service.update(20L, req);

    assertEquals("Time Updated", resp.title());

    ArgumentCaptor<EntityAuditEvent> captor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(captor.capture());
    EntityAuditEvent event = captor.getValue();
    assertEquals(AuditAction.UPDATE, event.getAction());
    assertEquals(AuditEntityType.MAGAZINE, event.getEntityType());
    assertEquals(20L, event.getEntityId());
    assertTrue(event.getDescription().contains("Time Updated"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void deletePublishesAuditEvent() {
    Magazine existing = makeMagazine(20L, "Doomed Mag");
    when(magazines.findById(20L)).thenReturn(Optional.of(existing));
    when(records.existsByMagazineId(20L)).thenReturn(false);

    service.delete(20L);

    verify(magazines).delete(existing);

    ArgumentCaptor<EntityAuditEvent> captor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(captor.capture());
    EntityAuditEvent event = captor.getValue();
    assertEquals(AuditAction.DELETE, event.getAction());
    assertEquals(AuditEntityType.MAGAZINE, event.getEntityType());
    assertEquals(20L, event.getEntityId());
    assertTrue(event.getDescription().contains("Doomed Mag"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void deleteWithBorrowHistoryThrows() {
    Magazine existing = makeMagazine(20L, "Borrowed Mag");
    when(magazines.findById(20L)).thenReturn(Optional.of(existing));
    when(records.existsByMagazineId(20L)).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.delete(20L));
    verify(events, never()).publishEvent(any());
  }

  @Test
  void updateNotFoundDoesNotPublishAudit() {
    when(magazines.findById(99L)).thenReturn(Optional.empty());

    MagazineRequest req =
        new MagazineRequest("Missing", "Publisher", LocalDate.of(2026, 1, 1), "Science", "Article");
    assertThrows(ResourceNotFoundException.class, () -> service.update(99L, req));
    verify(events, never()).publishEvent(any());
  }

  private Magazine makeMagazine(Long id, String title) {
    Magazine m = new Magazine();
    setId(m, id);
    m.setTitle(title);
    return m;
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
