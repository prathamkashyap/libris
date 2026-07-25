package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.Magazine;
import com.example.lms.exception.*;
import com.example.lms.repository.*;
import com.example.lms.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MagazineService {
    private final MagazineRepository magazines;
    private final BorrowRecordRepository records;

    public MagazineService(MagazineRepository magazines, BorrowRecordRepository records) {
        this.magazines = magazines;
        this.records = records;
    }

    @Transactional(readOnly=true)
    public Page<MagazineResponse> list(String search, Pageable pageable) {
        var source = search == null || search.isBlank() ? magazines.findAll(pageable)
                : magazines.searchMagazines(search, pageable);
        return source.map(this::response);
    }

    @Transactional(readOnly=true)
    public MagazineResponse get(Long id) {
        return response(magazine(id));
    }

    @Transactional
    public MagazineResponse create(MagazineRequest request) {
        var entity = new Magazine();
        apply(entity, request);
        entity.setAvailable(true);
        return save(entity);
    }

    @Transactional
    public MagazineResponse update(Long id, MagazineRequest request) {
        var entity = magazine(id);
        apply(entity, request);
        return save(entity);
    }

    @Transactional
    public void delete(Long id) {
        var entity = magazine(id);
        if (records.existsByMagazineId(id)) throw new ConflictException("A magazine with borrow history cannot be deleted.");
        magazines.delete(entity);
    }

    private Magazine magazine(Long id) {
        return magazines.findById(id).orElseThrow(() -> new ResourceNotFoundException("Magazine not found."));
    }

    private MagazineResponse save(Magazine magazine) {
        return response(magazines.saveAndFlush(magazine));
    }

    private void apply(Magazine entity, MagazineRequest r) {
        entity.setTitle(r.title().trim());
        entity.setPublisher(StringUtils.blankToNull(r.publisher()));
        entity.setCategory(StringUtils.blankToNull(r.category()));
        entity.setFeaturedArticle(StringUtils.blankToNull(r.featuredArticle()));
        entity.setIssueDate(r.issueDate());
    }

    private MagazineResponse response(Magazine m) {
        return new MagazineResponse(m.getId(), m.getTitle(), m.getPublisher(), m.getIssueDate(), m.getCategory(), m.getFeaturedArticle(), m.isAvailable());
    }
}
