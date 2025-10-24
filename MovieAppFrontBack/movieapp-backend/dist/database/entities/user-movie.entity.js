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
exports.UserMovieEntity = exports.MovieStatus = void 0;
const typeorm_1 = require("typeorm");
const user_entity_1 = require("./user.entity");
const movie_entity_1 = require("./movie.entity");
var MovieStatus;
(function (MovieStatus) {
    MovieStatus["WATCHED"] = "watched";
    MovieStatus["WATCHLIST"] = "watchlist";
})(MovieStatus || (exports.MovieStatus = MovieStatus = {}));
let UserMovieEntity = class UserMovieEntity {
};
exports.UserMovieEntity = UserMovieEntity;
__decorate([
    (0, typeorm_1.PrimaryGeneratedColumn)('uuid'),
    __metadata("design:type", String)
], UserMovieEntity.prototype, "id", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'user_id', type: 'uuid' }),
    __metadata("design:type", String)
], UserMovieEntity.prototype, "userId", void 0);
__decorate([
    (0, typeorm_1.ManyToOne)(() => user_entity_1.UserEntity, { onDelete: 'CASCADE' }),
    (0, typeorm_1.JoinColumn)({ name: 'user_id' }),
    __metadata("design:type", user_entity_1.UserEntity)
], UserMovieEntity.prototype, "user", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'movie_id', type: 'varchar', length: 255 }),
    __metadata("design:type", String)
], UserMovieEntity.prototype, "movieId", void 0);
__decorate([
    (0, typeorm_1.ManyToOne)(() => movie_entity_1.MovieEntity, { onDelete: 'CASCADE' }),
    (0, typeorm_1.JoinColumn)({ name: 'movie_id' }),
    __metadata("design:type", movie_entity_1.MovieEntity)
], UserMovieEntity.prototype, "movie", void 0);
__decorate([
    (0, typeorm_1.Column)({
        type: 'varchar',
        length: 20,
        enum: MovieStatus,
    }),
    __metadata("design:type", String)
], UserMovieEntity.prototype, "status", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'user_rating', type: 'decimal', precision: 3, scale: 1, nullable: true }),
    __metadata("design:type", Number)
], UserMovieEntity.prototype, "userRating", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'watched_date', type: 'date', nullable: true }),
    __metadata("design:type", Date)
], UserMovieEntity.prototype, "watchedDate", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'user_review', type: 'text', nullable: true }),
    __metadata("design:type", String)
], UserMovieEntity.prototype, "userReview", void 0);
__decorate([
    (0, typeorm_1.Column)({ name: 'is_favorite', type: 'boolean', default: false }),
    __metadata("design:type", Boolean)
], UserMovieEntity.prototype, "isFavorite", void 0);
__decorate([
    (0, typeorm_1.CreateDateColumn)({ name: 'created_at' }),
    __metadata("design:type", Date)
], UserMovieEntity.prototype, "createdAt", void 0);
__decorate([
    (0, typeorm_1.UpdateDateColumn)({ name: 'updated_at' }),
    __metadata("design:type", Date)
], UserMovieEntity.prototype, "updatedAt", void 0);
exports.UserMovieEntity = UserMovieEntity = __decorate([
    (0, typeorm_1.Entity)('user_movies'),
    (0, typeorm_1.Unique)(['userId', 'movieId'])
], UserMovieEntity);
//# sourceMappingURL=user-movie.entity.js.map