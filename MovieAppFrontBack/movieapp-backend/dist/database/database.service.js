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
const movie_interface_1 = require("../common/interfaces/movie.interface");
let DatabaseService = DatabaseService_1 = class DatabaseService {
    constructor(movieRepository, tmdbCacheRepository) {
        this.movieRepository = movieRepository;
        this.tmdbCacheRepository = tmdbCacheRepository;
        this.logger = new common_1.Logger(DatabaseService_1.name);
        this.enrichmentSessions = new Map();
        this.analyticsCache = new Map();
    }
    async onModuleInit() {
        try {
            const movieCount = await this.movieRepository.count();
            const cacheCount = await this.tmdbCacheRepository.count();
            const enrichedCount = await this.movieRepository.count({
                where: { is_enriched: true }
            });
            this.logger.log(`✅ database connesso`);
            this.logger.log(`film totali: ${movieCount}`);
            this.logger.log(`film arricchiti: ${enrichedCount}`);
            this.logger.log(`cache tmdb: ${cacheCount} entries`);
            await this.cleanExpiredTmdbCache();
        }
        catch (error) {
            this.logger.error(`❌ errore connessione database: ${error.message}`);
            throw error;
        }
    }
    async getTmdbCache(cacheKey) {
        try {
            const cached = await this.tmdbCacheRepository.findOne({
                where: { cache_key: cacheKey },
            });
            if (!cached) {
                this.logger.debug(`cache miss: ${cacheKey}`);
                return null;
            }
            if (cached.isExpired()) {
                this.logger.debug(`cache expired: ${cacheKey}, rimozione automatica`);
                await this.tmdbCacheRepository.remove(cached);
                return null;
            }
            cached.incrementHit();
            await this.tmdbCacheRepository.save(cached);
            this.logger.debug(`✅ cache hit: ${cacheKey} (hits: ${cached.hit_count})`);
            return cached.tmdb_data;
        }
        catch (error) {
            this.logger.error(`errore recupero cache ${cacheKey}: ${error.message}`);
            return null;
        }
    }
    async saveTmdbCache(cacheKey, tmdbData) {
        try {
            const cacheEntry = new tmdb_cache_entity_1.TmdbCacheEntity();
            cacheEntry.cache_key = cacheKey;
            cacheEntry.tmdb_data = tmdbData;
            cacheEntry.tmdb_id = tmdbData.id;
            cacheEntry.title = tmdbData.title;
            cacheEntry.year = tmdbData.release_date
                ? new Date(tmdbData.release_date).getFullYear()
                : undefined;
            cacheEntry.imdb_id = tmdbData.imdb_id;
            cacheEntry.setExpiry(30);
            await this.tmdbCacheRepository.save(cacheEntry);
            this.logger.debug(`cache salvata: ${cacheKey} (tmdb_id: ${tmdbData.id})`);
        }
        catch (error) {
            if (error.code === '23505') {
                try {
                    await this.tmdbCacheRepository.update({ cache_key: cacheKey }, {
                        tmdb_data: tmdbData,
                        tmdb_id: tmdbData.id,
                        title: tmdbData.title,
                        year: tmdbData.release_date
                            ? new Date(tmdbData.release_date).getFullYear()
                            : undefined,
                        imdb_id: tmdbData.imdb_id,
                        updated_at: new Date(),
                    });
                    this.logger.debug(`cache aggiornata: ${cacheKey}`);
                }
                catch (updateError) {
                    this.logger.error(`errore aggiornamento cache: ${updateError.message}`);
                }
            }
            else {
                this.logger.error(`errore salvataggio cache: ${error.message}`);
            }
        }
    }
    async cleanExpiredTmdbCache() {
        try {
            const result = await this.tmdbCacheRepository
                .createQueryBuilder()
                .delete()
                .where('expires_at < :now', { now: new Date() })
                .execute();
            const deleted = result.affected || 0;
            if (deleted > 0) {
                this.logger.log(`🧹 pulite ${deleted} entries cache scadute`);
            }
            return deleted;
        }
        catch (error) {
            this.logger.error(`errore pulizia cache: ${error.message}`);
            return 0;
        }
    }
    async getTmdbCacheStats() {
        try {
            const [totalEntries, recentEntries, expiredEntries, topHits] = await Promise.all([
                this.tmdbCacheRepository.count(),
                this.tmdbCacheRepository
                    .createQueryBuilder()
                    .where('created_at >= :date', {
                    date: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
                })
                    .getCount(),
                this.tmdbCacheRepository
                    .createQueryBuilder()
                    .where('expires_at <= :now', { now: new Date() })
                    .getCount(),
                this.tmdbCacheRepository.find({
                    order: { hit_count: 'DESC' },
                    take: 10,
                }),
            ]);
            return {
                totalEntries,
                recentEntries,
                expiredEntries,
                topHits: topHits.map((entry) => ({
                    title: entry.title,
                    hits: entry.hit_count,
                    lastAccessed: entry.last_accessed_at,
                    tmdbId: entry.tmdb_id,
                })),
                cacheEfficiency: totalEntries > 0
                    ? (((totalEntries - expiredEntries) / totalEntries) * 100).toFixed(1)
                    : '0',
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche cache: ${error.message}`);
            return { error: error.message, totalEntries: 0 };
        }
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
    async saveMovies(movies) {
        try {
            this.logger.log(`📦 avvio salvataggio batch: ${movies.length} film`);
            if (movies.length === 0) {
                this.logger.warn('⚠️ nessun film da salvare');
                return [];
            }
            const entities = movies.map((movie) => {
                const entity = this.movieToEntity(movie);
                entity.is_enriched = !!(entity.tmdb_id && entity.tmdb_id > 0);
                return entity;
            });
            const savedEntities = await this.movieRepository.manager.transaction(async (transactionalEntityManager) => {
                const savedResults = [];
                const CHUNK_SIZE = 500;
                this.logger.log(`salvataggio in ${Math.ceil(entities.length / CHUNK_SIZE)} chunk da max ${CHUNK_SIZE} film`);
                for (let i = 0; i < entities.length; i += CHUNK_SIZE) {
                    const chunk = entities.slice(i, i + CHUNK_SIZE);
                    const chunkNumber = Math.floor(i / CHUNK_SIZE) + 1;
                    this.logger.log(`chunk ${chunkNumber}: ${chunk.length} film (${i + 1}-${i + chunk.length})`);
                    try {
                        const saved = await transactionalEntityManager.save(movie_entity_1.MovieEntity, chunk, {
                            chunk: CHUNK_SIZE,
                        });
                        savedResults.push(...saved);
                        this.logger.log(`✅ chunk ${chunkNumber} salvato: ${saved.length} film (totale: ${savedResults.length})`);
                    }
                    catch (chunkError) {
                        this.logger.error(`❌ errore chunk ${chunkNumber}: ${chunkError.message}`);
                        this.logger.log(`🔄 fallback: salvataggio individuale chunk ${chunkNumber}`);
                        for (const entity of chunk) {
                            try {
                                const individual = await transactionalEntityManager.save(movie_entity_1.MovieEntity, entity);
                                savedResults.push(individual);
                                this.logger.debug(`✅ salvato individualmente: ${entity.title}`);
                            }
                            catch (individualError) {
                                this.logger.error(`❌ fallito salvataggio ${entity.title}: ${individualError.message}`);
                            }
                        }
                    }
                }
                return savedResults;
            });
            const enrichedCount = savedEntities.filter((e) => e.is_enriched).length;
            this.logger.log(`=== BATCH COMPLETATO ===`);
            this.logger.log(`film richiesti: ${movies.length}`);
            this.logger.log(`film salvati: ${savedEntities.length}`);
            this.logger.log(`film arricchiti: ${enrichedCount}`);
            this.logger.log(`success rate: ${((savedEntities.length / movies.length) * 100).toFixed(1)}%`);
            return savedEntities;
        }
        catch (error) {
            this.logger.error(`❌ errore critico salvataggio batch: ${error.message}`);
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
            const watchedCount = movies.filter((m) => m.is_watched).length;
            this.logger.log(`📚 recuperati ${movies.length} film (${enrichedCount} arricchiti, ${watchedCount} visti)`);
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
    async deleteAllMovies() {
        try {
            const result = await this.movieRepository.delete({});
            this.logger.log(`🗑️ eliminati ${result.affected} film dal database`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film: ${error.message}`);
            throw error;
        }
    }
    async deleteMoviesBySource(source) {
        try {
            const result = await this.movieRepository.delete({ source: source });
            this.logger.log(`🗑️ eliminati ${result.affected} film da ${source}`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film da ${source}: ${error.message}`);
            throw error;
        }
    }
    async getStats() {
        try {
            const [totalMovies, watchedCount, enrichedCount, unenrichedCount, imdbMovies, letterboxdMovies, cacheStats,] = await Promise.all([
                this.movieRepository.count(),
                this.movieRepository.count({ where: { is_watched: true } }),
                this.movieRepository.count({ where: { is_enriched: true } }),
                this.movieRepository.count({ where: { is_enriched: false } }),
                this.movieRepository.count({ where: { source: movie_interface_1.DataSource.IMDB } }),
                this.movieRepository.count({ where: { source: movie_interface_1.DataSource.LETTERBOXD } }),
                this.getTmdbCacheStats(),
            ]);
            const stats = {
                database: {
                    totalMovies,
                    enrichedMovies: enrichedCount,
                    unenrichedMovies: unenrichedCount,
                    watchedCount,
                    watchlistCount: totalMovies - watchedCount,
                    imdbCount: imdbMovies,
                    letterboxdCount: letterboxdMovies,
                    enrichmentRate: totalMovies > 0 ? ((enrichedCount / totalMovies) * 100).toFixed(1) : '0',
                },
                tmdbCache: cacheStats,
                system: {
                    databaseType: 'postgresql',
                    cacheEnabled: true,
                    enrichmentOptimized: true,
                    lastUpdated: new Date().toISOString(),
                },
            };
            this.logger.debug(`statistiche recuperate: ${totalMovies} film totali`);
            return stats;
        }
        catch (error) {
            this.logger.error(`errore recupero statistiche: ${error.message}`);
            return {
                error: error.message,
                database: { totalMovies: 0, enrichedMovies: 0 },
            };
        }
    }
    async healthCheck() {
        try {
            const stats = await this.getStats();
            const cacheStats = await this.getTmdbCacheStats();
            return {
                status: 'healthy',
                details: {
                    database_type: 'postgresql',
                    connection: 'active',
                    movies: stats.database,
                    tmdb_cache: cacheStats,
                    enrichment_system: 'optimized with is_enriched flag',
                    timestamp: new Date().toISOString(),
                },
            };
        }
        catch (error) {
            return {
                status: 'unhealthy',
                details: {
                    error: error.message,
                    database_type: 'postgresql',
                    timestamp: new Date().toISOString(),
                },
            };
        }
    }
    async setEnrichmentStatus(sessionId, status) {
        try {
            this.enrichmentSessions.set(sessionId, { sessionId, ...status });
            this.logger.debug(`enrichment status aggiornato: ${sessionId} (${status.processed}/${status.total})`);
        }
        catch (error) {
            this.logger.error(`errore aggiornamento status ${sessionId}: ${error.message}`);
        }
    }
    async getEnrichmentStatus(sessionId) {
        try {
            const status = this.enrichmentSessions.get(sessionId);
            if (status) {
                this.logger.debug(`enrichment status recuperato: ${sessionId}`);
            }
            return status || null;
        }
        catch (error) {
            this.logger.error(`errore recupero status ${sessionId}: ${error.message}`);
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
            this.logger.log(`📊 analytics recuperate per utente: ${userId}`);
            return cached.data;
        }
        catch (error) {
            this.logger.error(`errore recupero analytics ${userId}: ${error.message}`);
            return null;
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
                this.logger.debug(`film non trovato per tmdb_id: ${tmdbId}`);
                return null;
            }
            return this.entityToMovie(entity);
        }
        catch (error) {
            this.logger.error(`errore recupero film tmdb_id ${tmdbId}: ${error.message}`);
            return null;
        }
    }
    async saveOrUpdateMovie(movie) {
        try {
            let existing = await this.findMovieByTitleYear(movie.title, movie.year);
            if (existing) {
                return await this.updateMovie(existing.id, movie);
            }
            else {
                const entity = await this.saveMovie(movie);
                return this.entityToMovie(entity);
            }
        }
        catch (error) {
            this.logger.error(`errore save/update film ${movie.title}: ${error.message}`);
            throw error;
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
    async associateMovieWithUser(userId, movieId, isWatched) {
        this.logger.debug(`associazione film ${movieId} con utente ${userId} (watched: ${isWatched})`);
    }
    async getUserMovies(userId) {
        this.logger.debug(`recupero film per utente ${userId}`);
        return await this.getAllMovies();
    }
    movieToEntity(movie) {
        const entity = new movie_entity_1.MovieEntity();
        entity.id = movie.id;
        entity.title = movie.title;
        entity.year = movie.year;
        entity.user_rating = movie.user_rating;
        entity.watched_date = movie.watched_date;
        entity.user_review = movie.user_review;
        entity.is_watched = movie.is_watched;
        entity.source = movie.source;
        entity.tmdb_id = movie.tmdb_id;
        entity.genres = movie.genres?.length > 0 ? movie.genres : undefined;
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
        entity.production_companies =
            movie.production_companies?.length > 0 ? movie.production_companies : undefined;
        entity.production_countries =
            movie.production_countries?.length > 0 ? movie.production_countries : undefined;
        entity.spoken_languages =
            movie.spoken_languages?.length > 0 ? movie.spoken_languages : undefined;
        entity.keywords = movie.keywords?.length > 0 ? movie.keywords : undefined;
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
            user_rating: entity.user_rating,
            watched_date: entity.watched_date,
            user_review: entity.user_review,
            is_watched: entity.is_watched,
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