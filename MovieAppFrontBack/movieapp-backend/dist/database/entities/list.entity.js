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
exports.MovieListEntity = void 0;
const typeorm_1 = require("typeorm");
let MovieListEntity = class MovieListEntity {
};
exports.MovieListEntity = MovieListEntity;
__decorate([
    (0, typeorm_1.PrimaryGeneratedColumn)('uuid'),
    __metadata("design:type", String)
], MovieListEntity.prototype, "id", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'user_id', type: 'uuid' }),
    __metadata("design:type", String)
], MovieListEntity.prototype, "user_id", void 0);
__decorate([
    (0, typeorm_1.Column)({ type: 'varchar', length: 200 }),
    __metadata("design:type", String)
], MovieListEntity.prototype, "name", void 0);
__decorate([
    (0, typeorm_1.Column)({ type: 'text', nullable: true }),
    __metadata("design:type", String)
], MovieListEntity.prototype, "description", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'movie_ids', type: 'text', array: true, default: '{}' }),
    __metadata("design:type", Array)
], MovieListEntity.prototype, "movie_ids", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'is_public', type: 'boolean', default: false }),
    __metadata("design:type", Boolean)
], MovieListEntity.prototype, "is_public", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'follower_ids', type: 'text', array: true, default: '{}' }),
    __metadata("design:type", Array)
], MovieListEntity.prototype, "follower_ids", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'followers_count', type: 'int', default: 0 }),
    __metadata("design:type", Number)
], MovieListEntity.prototype, "followers_count", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'target_date', type: 'date', nullable: true }),
    __metadata("design:type", Date)
], MovieListEntity.prototype, "target_date", void 0);
__decorate([
    (0, typeorm_1.Column)({ type: 'varchar', length: 50, nullable: true }),
    __metadata("design:type", String)
], MovieListEntity.prototype, "frequency", void 0);
__decorate([
    (0, typeorm_1.CreateDateColumn)({ name: 'created_at' }),
    __metadata("design:type", Date)
], MovieListEntity.prototype, "created_at", void 0);
__decorate([
    (0, typeorm_1.UpdateDateColumn)({ name: 'updated_at' }),
    __metadata("design:type", Date)
], MovieListEntity.prototype, "updated_at", void 0);
exports.MovieListEntity = MovieListEntity = __decorate([
    (0, typeorm_1.Entity)('movie_lists')
], MovieListEntity);
//# sourceMappingURL=list.entity.js.map