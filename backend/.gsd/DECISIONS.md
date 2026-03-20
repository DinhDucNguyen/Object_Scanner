# DECISIONS.md — Architecture Decision Records

## ADR-001: Layered Architecture Pattern
**Date**: 2026-03-02
**Decision**: Router → Service → Repository → Model
**Rationale**: Tách biệt responsibility, dễ test, dễ maintain

## ADR-002: 1 Model = 1 File
**Date**: 2026-03-10
**Decision**: Mỗi SQLAlchemy model nằm trong file riêng
**Rationale**: Dễ tìm, dễ quản lý, clean git diff

## ADR-003: YOLOv8 + ML Kit + Gemini API
**Date**: 2026-03-10
**Decision**: 3-tier object recognition
**Rationale**: YOLOv8 pre-trained cho accuracy, ML Kit cho on-device speed, Gemini API cho fallback khi chưa train

## ADR-004: Kotlin Native (not Flutter/React Native)
**Date**: 2026-03-10
**Decision**: Android native với Kotlin
**Rationale**: Best camera/ML Kit integration, graduation project requirement

## ADR-005: Vietnamese → English (MVP)
**Date**: 2026-03-10
**Decision**: Chỉ hỗ trợ Việt-Anh cho v1.0
**Rationale**: Đủ cho đồ án, mở rộng sau deadline
