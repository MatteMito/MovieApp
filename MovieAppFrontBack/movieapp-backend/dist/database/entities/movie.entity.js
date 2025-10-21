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
exports.MovieEntity = void 0;
const typeorm_1 = require("typeorm");
let MovieEntity = class MovieEntity {
    get isWatched() {
        return this.is_watched;
    }
};
exports.MovieEntity = MovieEntity;
__decorate([
    (0, typeorm_1.PrimaryColumn)(),
    __metadata("design:type", String)
], MovieEntity.prototype, "id", void 0);
__decorate([
    (0, typeorm_1.Column)({ length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "title", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "year", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 255 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "director", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { array: true, default: [] }),
    __metadata("design:type", Array)
], MovieEntity.prototype, "genres", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { array: true, default: [] }),
    __metadata("design:type", Array)
], MovieEntity.prototype, "cast", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { nullable: true }),
    __metadata("design:type", String)
], MovieEntity.prototype, "overview", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "tagline", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "runtime", void 0);
__decorate([
    (0, typeorm_1.Column)('decimal', { precision: 3, scale: 1, nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "user_rating", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", String)
], MovieEntity.prototype, "date_rated", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 100 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "watched_date", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { nullable: true }),
    __metadata("design:type", String)
], MovieEntity.prototype, "user_review", void 0);
__decorate([
    (0, typeorm_1.Column)({ default: false }),
    __metadata("design:type", Boolean)
], MovieEntity.prototype, "is_watched", void 0);
__decorate([
    (0, typeorm_1.Column)({ default: false }),
    __metadata("design:type", Boolean)
], MovieEntity.prototype, "is_enriched", void 0);
__decorate([
    (0, typeorm_1.Column)({ length: 50 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "source", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "tmdb_id", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "poster_url", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "backdrop_url", void 0);
__decorate([
    (0, typeorm_1.Column)('decimal', { precision: 3, scale: 1, nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "tmdb_rating", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "vote_count", void 0);
__decorate([
    (0, typeorm_1.Column)('bigint', { nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "budget", void 0);
__decorate([
    (0, typeorm_1.Column)('bigint', { nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "revenue", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 100 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "status", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 10 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "original_language", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "original_title", void 0);
__decorate([
    (0, typeorm_1.Column)('decimal', { precision: 10, scale: 3, nullable: true }),
    __metadata("design:type", Number)
], MovieEntity.prototype, "popularity", void 0);
__decorate([
    (0, typeorm_1.Column)({ default: false }),
    __metadata("design:type", Boolean)
], MovieEntity.prototype, "adult", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "homepage", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 20 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "imdb_id", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { array: true, default: [] }),
    __metadata("design:type", Array)
], MovieEntity.prototype, "production_companies", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { array: true, default: [] }),
    __metadata("design:type", Array)
], MovieEntity.prototype, "production_countries", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { array: true, default: [] }),
    __metadata("design:type", Array)
], MovieEntity.prototype, "spoken_languages", void 0);
__decorate([
    (0, typeorm_1.Column)('text', { array: true, default: [] }),
    __metadata("design:type", Array)
], MovieEntity.prototype, "keywords", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 20 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "certification", void 0);
__decorate([
    (0, typeorm_1.Column)({ nullable: true, length: 500 }),
    __metadata("design:type", String)
], MovieEntity.prototype, "trailer_url", void 0);
__decorate([
    (0, typeorm_1.CreateDateColumn)(),
    __metadata("design:type", Date)
], MovieEntity.prototype, "created_at", void 0);
__decorate([
    (0, typeorm_1.UpdateDateColumn)(),
    __metadata("design:type", Date)
], MovieEntity.prototype, "updated_at", void 0);
exports.MovieEntity = MovieEntity = __decorate([
    (0, typeorm_1.Entity)('movies'),
    (0, typeorm_1.Index)(['title']),
    (0, typeorm_1.Index)(['year']),
    (0, typeorm_1.Index)(['director']),
    (0, typeorm_1.Index)(['tmdb_id']),
    (0, typeorm_1.Index)(['is_watched']),
    (0, typeorm_1.Index)(['source'])
], MovieEntity);
//# sourceMappingURL=movie.entity.js.map