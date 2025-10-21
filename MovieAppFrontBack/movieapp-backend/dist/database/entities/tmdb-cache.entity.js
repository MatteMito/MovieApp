"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbCacheEntity = void 0;
const typeorm_1 = require("typeorm");
let TmdbCacheEntity = class TmdbCacheEntity {
    isExpired() {
        return new Date() > this.expires_at;
    }
    incrementHit() {
        this.hit_count++;
        this.last_accessed_at = new Date();
    }
    setExpiry(days = 30) {
        const expiryDate = new Date();
        expiryDate.setDate(expiryDate.getDate() + days);
        this.expires_at = expiryDate;
    }
};
exports.TmdbCacheEntity = TmdbCacheEntity;
__decorate([
    (0, typeorm_1.PrimaryColumn)(),
    __metadata("design:type", String)
], TmdbCacheEntity.prototype, "cache_key", void 0);
__decorate([
    (0, typeorm_1.Column)(),
    __metadata("design:type", Number)
], TmdbCacheEntity.prototype, "tmdb_id", void 0);
__decorate([
    (0, typeorm_1.Column)('jsonb'),
    __metadata("design:type", Object)
], TmdbCacheEntity.prototype, "tmdb_data", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", String)
], TmdbCacheEntity.prototype, "title", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", Number)
], TmdbCacheEntity.prototype, "year", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", String)
], TmdbCacheEntity.prototype, "imdb_id", void 0);
__decorate([
    (0, typeorm_1.Column)({ default: 0 }),
    __metadata("design:type", Number)
], TmdbCacheEntity.prototype, "hit_count", void 0);
__decorate([
    (0, typeorm_1.Column)({ type: 'timestamp', nullable: true }),
    __metadata("design:type", Date)
], TmdbCacheEntity.prototype, "last_accessed_at", void 0);
__decorate([
    (0, typeorm_1.Column)({ type: 'timestamp' }),
    __metadata("design:type", Date)
], TmdbCacheEntity.prototype, "expires_at", void 0);
__decorate([
    (0, typeorm_1.CreateDateColumn)(),
    __metadata("design:type", Date)
], TmdbCacheEntity.prototype, "created_at", void 0);
__decorate([
    (0, typeorm_1.UpdateDateColumn)(),
    __metadata("design:type", Date)
], TmdbCacheEntity.prototype, "updated_at", void 0);
exports.TmdbCacheEntity = TmdbCacheEntity = __decorate([
    (0, typeorm_1.Entity)('tmdb_cache'),
    (0, typeorm_1.Index)(['tmdb_id']),
    (0, typeorm_1.Index)(['expires_at'])
], TmdbCacheEntity);
//# sourceMappingURL=tmdb-cache.entity.js.map