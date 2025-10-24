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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var DatabaseService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatabaseService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const movie_entity_1 = require("./entities/movie.entity");
const tmdb_cache_entity_1 = require("./entities/tmdb-cache.entity");
let DatabaseService = DatabaseService_1 = class DatabaseService {
    constructor(movieRepository, tmdbCacheRepository) {
        this.movieRepository = movieRepository;
        this.tmdbCacheRepository = tmdbCacheRepository;
        this.logger = new common_1.Logger(DatabaseService_1.name);
        this.analyticsCache = new Map();
    }
    movieToEntity(movie) {
        const entity = new movie_entity_1.MovieEntity();
        entity.id = movie.id;
        entity.title = movie.title;
        entity.year = movie.year;
        entity.source = movie.source;
        entity.tmdb_id = movie.tmdb_id;
        entity.genres = movie.genres?.length > 0 ? movie.genres : [];
        entity.director = movie.director;
        entity.actors = movie.actors?.length > 0 ? movie.actors : undefined;
        entity.overview = movie.overview;
        entity.tagline = movie.tagline;
        entity.poster_url = movie.poster_url;
        entity.backdrop_url = movie.backdrop_url;
        entity.tmdb_rating = movie.tmdb_rating;
        entity.vote_count = movie.vote_count;
        entity.runtime = movie.runtime;
        entity.budget = movie.budget;
        entity.revenue = movie.revenue;
        entity.status = movie.status;
        entity.original_language = movie.original_language;
        entity.original_title = movie.original_title;
        entity.popularity = movie.popularity;
        entity.adult = movie.adult;
        entity.homepage = movie.homepage;
        entity.imdb_id = movie.imdb_id;
        entity.production_companies = movie.production_companies?.length > 0 ? movie.production_companies : [];
        entity.production_countries = movie.production_countries?.length > 0 ? movie.production_countries : [];
        entity.spoken_languages = movie.spoken_languages?.length > 0 ? movie.spoken_languages : [];
        entity.keywords = movie.keywords?.length > 0 ? movie.keywords : [];
        entity.certification = movie.certification;
        entity.trailer_url = movie.trailer_url;
        entity.is_enriched = !!(movie.tmdb_id && movie.tmdb_id > 0);
        return entity;
    }
    entityToMovie(entity) {
        return {
            id: entity.id,
            title: entity.title,
            year: entity.year,
            source: entity.source,
            tmdb_id: entity.tmdb_id,
            genres: entity.genres || [],
            director: entity.director,
            actors: entity.actors || [],
            overview: entity.overview,
            tagline: entity.tagline,
            poster_url: entity.poster_url,
            backdrop_url: entity.backdrop_url,
            tmdb_rating: entity.tmdb_rating,
            vote_count: entity.vote_count,
            runtime: entity.runtime,
            budget: entity.budget,
            revenue: entity.revenue,
            status: entity.status,
            original_language: entity.original_language,
            original_title: entity.original_title,
            popularity: entity.popularity,
            adult: entity.adult,
            homepage: entity.homepage,
            imdb_id: entity.imdb_id,
            production_companies: entity.production_companies || [],
            production_countries: entity.production_countries || [],
            spoken_languages: entity.spoken_languages || [],
            keywords: entity.keywords || [],
            certification: entity.certification,
            trailer_url: entity.trailer_url,
        };
    }
    async saveMovie(movie) {
        try {
            const movieEntity = this.movieToEntity(movie);
            movieEntity.is_enriched = !!(movieEntity.tmdb_id && movieEntity.tmdb_id > 0);
            const saved = await this.movieRepository.save(movieEntity);
            this.logger.debug(`film salvato: ${saved.title} (arricchito: ${saved.is_enriched})`);
            return saved;
        }
        catch (error) {
            this.logger.error(`errore salvataggio film ${movie.title}: ${error.message}`);
            throw error;
        }
    }
    async saveMovies(movies) {
        try {
            this.logger.log(`📦 avvio salvataggio batch: ${movies.length} film`);
            if (movies.length === 0) {
                this.logger.warn('⚠️ nessun film da salvare');
                return [];
            }
            const entities = movies.map((movie) => {
                const entity = this.movieToEntity(movie);
                entity.is_enriched = !!(movie.tmdb_id && movie.tmdb_id > 0);
                return entity;
            });
            const chunkSize = 100;
            const savedEntities = [];
            for (let i = 0; i < entities.length; i += chunkSize) {
                const chunk = entities.slice(i, i + chunkSize);
                const saved = await this.movieRepository.save(chunk);
                savedEntities.push(...saved);
                this.logger.log(`💾 salvati ${saved.length} film (chunk ${Math.floor(i / chunkSize) + 1})`);
            }
            const enrichedCount = savedEntities.filter((e) => e.is_enriched).length;
            this.logger.log(`✅ batch completato: ${savedEntities.length} film salvati (${enrichedCount} arricchiti)`);
            return savedEntities;
        }
        catch (error) {
            this.logger.error(`errore salvataggio batch: ${error.message}`);
            throw error;
        }
    }
    async getAllMovies() {
        try {
            const entities = await this.movieRepository.find({
                order: { title: 'ASC' },
            });
            const movies = entities.map((entity) => this.entityToMovie(entity));
            const enrichedCount = movies.filter((m) => m.tmdb_id).length;
            this.logger.log(`📚 recuperati ${movies.length} film (${enrichedCount} arricchiti)`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore recupero film: ${error.message}`);
            return [];
        }
    }
    async getMovieById(id) {
        try {
            const entity = await this.movieRepository.findOne({ where: { id } });
            if (!entity) {
                this.logger.debug(`film non trovato per id: ${id}`);
                return null;
            }
            return this.entityToMovie(entity);
        }
        catch (error) {
            this.logger.error(`errore recupero film ${id}: ${error.message}`);
            return null;
        }
    }
    async getMoviesByIds(movieIds) {
        try {
            if (movieIds.length === 0) {
                return [];
            }
            const entities = await this.movieRepository.findByIds(movieIds);
            const movies = entities.map((entity) => this.entityToMovie(entity));
            this.logger.debug(`📚 recuperati ${movies.length} film da ${movieIds.length} IDs`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore recupero film by IDs: ${error.message}`);
            return [];
        }
    }
    async findMovieByTitleYear(title, year) {
        try {
            const whereConditions = { title };
            if (year)
                whereConditions.year = year;
            let entity = await this.movieRepository.findOne({
                where: whereConditions,
            });
            if (!entity && title) {
                entity = await this.movieRepository
                    .createQueryBuilder('movie')
                    .where('LOWER(movie.title) = LOWER(:title)', { title })
                    .andWhere(year ? 'movie.year = :year' : '1=1', { year })
                    .getOne();
            }
            if (entity) {
                this.logger.debug(`film trovato: ${title} (${year}) - arricchito: ${entity.is_enriched}`);
                return this.entityToMovie(entity);
            }
            return null;
        }
        catch (error) {
            this.logger.error(`errore ricerca film ${title}: ${error.message}`);
            return null;
        }
    }
    async getUnenrichedMovies() {
        try {
            const entities = await this.movieRepository.find({
                where: { is_enriched: false },
                order: { created_at: 'ASC' },
            });
            const movies = entities.map((entity) => this.entityToMovie(entity));
            this.logger.log(`📊 trovati ${movies.length} film da arricchire`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore recupero film non arricchiti: ${error.message}`);
            return [];
        }
    }
    async updateMovie(id, updates) {
        try {
            const entity = await this.movieRepository.findOne({ where: { id } });
            if (!entity) {
                throw new Error(`Movie ${id} not found`);
            }
            Object.assign(entity, this.movieToEntity({ ...this.entityToMovie(entity), ...updates }));
            const updated = await this.movieRepository.save(entity);
            this.logger.debug(`✅ film ${id} aggiornato`);
            return this.entityToMovie(updated);
        }
        catch (error) {
            this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
            throw error;
        }
    }
    async deleteMovie(id) {
        try {
            const entity = await this.movieRepository.findOne({ where: { id } });
            if (!entity) {
                throw new Error(`Movie ${id} not found`);
            }
            await this.movieRepository.remove(entity);
            this.logger.debug(`✅ film ${id} eliminato`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film ${id}: ${error.message}`);
            throw error;
        }
    }
    async getMovieByTmdbId(tmdbId) {
        try {
            const entity = await this.movieRepository.findOne({
                where: { tmdb_id: tmdbId }
            });
            if (!entity) {
                return null;
            }
            return this.entityToMovie(entity);
        }
        catch (error) {
            this.logger.error(`errore recupero film tmdb_id ${tmdbId}: ${error.message}`);
            return null;
        }
    }
    async saveTmdbCache(cacheKey, tmdbData) {
        try {
            const entity = new tmdb_cache_entity_1.TmdbCacheEntity();
            entity.cache_key = cacheKey;
            entity.tmdb_id = tmdbData.id;
            entity.tmdb_data = tmdbData;
            entity.title = tmdbData.title;
            entity.year = tmdbData.release_date?.substring(0, 4);
            entity.imdb_id = tmdbData.imdb_id;
            entity.expires_at = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
            await this.tmdbCacheRepository.save(entity);
            this.logger.debug(`💾 cache tmdb salvata: ${cacheKey}`);
        }
        catch (error) {
            this.logger.error(`errore salvataggio cache ${cacheKey}: ${error.message}`);
        }
    }
    async getTmdbCache(cacheKey) {
        try {
            const cached = await this.tmdbCacheRepository.findOne({
                where: { cache_key: cacheKey },
            });
            if (!cached) {
                return null;
            }
            if (cached.expires_at && cached.expires_at < new Date()) {
                await this.tmdbCacheRepository.remove(cached);
                this.logger.debug(`🗑️ cache scaduta rimossa: ${cacheKey}`);
                return null;
            }
            cached.hit_count = (cached.hit_count || 0) + 1;
            cached.last_accessed_at = new Date();
            await this.tmdbCacheRepository.save(cached);
            this.logger.debug(`✅ cache hit: ${cacheKey}`);
            return cached.tmdb_data;
        }
        catch (error) {
            this.logger.error(`errore recupero cache ${cacheKey}: ${error.message}`);
            return null;
        }
    }
    async getCachedTmdbData(title, year) {
        try {
            const cacheKey = `${title.toLowerCase()}_${year || 'unknown'}`;
            return await this.getTmdbCache(cacheKey);
        }
        catch (error) {
            this.logger.error(`errore recupero cache per ${title}: ${error.message}`);
            return null;
        }
    }
    async saveAnalytics(userId, analyticsData) {
        try {
            const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000);
            this.analyticsCache.set(userId, { data: analyticsData, expiresAt });
            this.logger.log(`💾 analytics salvate per utente: ${userId}`);
        }
        catch (error) {
            this.logger.error(`errore salvataggio analytics ${userId}: ${error.message}`);
            throw error;
        }
    }
    async getAnalytics(userId) {
        try {
            const cached = this.analyticsCache.get(userId);
            if (!cached)
                return null;
            if (cached.expiresAt < new Date()) {
                this.analyticsCache.delete(userId);
                return null;
            }
            return cached.data;
        }
        catch (error) {
            this.logger.error(`errore recupero analytics ${userId}: ${error.message}`);
            return null;
        }
    }
    isDatabaseAvailable() {
        return this.movieRepository !== undefined && this.tmdbCacheRepository !== undefined;
    }
};
exports.DatabaseService = DatabaseService;
exports.DatabaseService = DatabaseService = DatabaseService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(tmdb_cache_entity_1.TmdbCacheEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository])
], DatabaseService);
//# sourceMappingURL=database.service.js.map