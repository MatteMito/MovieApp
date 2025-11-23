import { TmdbService } from './tmdb.service';
import { ConfigService } from '@nestjs/config';
export declare class TmdbScheduler {
    private readonly tmdbService;
    private readonly configService;
    private readonly logger;
    private readonly enableAutoSync;
    constructor(tmdbService: TmdbService, configService: ConfigService);
    syncPopularMoviesFull(): Promise<void>;
    syncPopularMoviesIncremental(): Promise<void>;
    testSync(): Promise<{
        synced: number;
        errors: number;
    }>;
}
